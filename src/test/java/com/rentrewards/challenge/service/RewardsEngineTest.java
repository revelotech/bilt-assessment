package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.MemberAccount;
import com.rentrewards.challenge.model.PaymentEvent;
import com.rentrewards.challenge.model.PointsResult;
import com.rentrewards.challenge.model.ProcessingOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardsEngineTest {

    private RewardsEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RewardsEngine(new PointsCalculator(), new ProcessedEventStore());
    }

    @Test
    void awardsOnePointPerDollarByDefault() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);

        assertEquals(1500, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
        assertFalse(result.isSkippedAsDuplicate());
    }

    @Test
    void appliesLinkedAccountMultiplier() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), true, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);

        assertEquals(3000, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
    }

    @Test
    void appliesStreakBonusWhenEligible() {
        MemberAccount member = new MemberAccount("member-1", 6);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("2000"), true, LocalDate.of(2026, 3, 1));

        // base = 2000 * 2 (linked) = 4000; +10% streak bonus = 4400
        PointsResult result = engine.processPayment(event, member);

        assertEquals(4400, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
    }

    @Test
    void enforcesMonthlyPointsCap() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("150000"), false, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);

        assertEquals(100_000, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
    }

    @Test
    void reportsCappedWhenALaterEventCannotAwardMorePoints() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent first = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("150000"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent second = new PaymentEvent("evt-2", "member-1",
                new BigDecimal("50"), false, LocalDate.of(2026, 3, 2));

        engine.processPayment(first, member);
        PointsResult result = engine.processPayment(second, member);

        assertEquals(0, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.CAPPED, result.getOutcome());
        assertFalse(result.isSkippedAsDuplicate());
        assertEquals(100_000, member.getPointsForMonth(YearMonth.of(2026, 3)));
    }

    @Test
    void doesNotDoubleAwardPointsWhenSameWebhookEventIsResent() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        PointsResult first = engine.processPayment(event, member);
        PointsResult retry = engine.processPayment(event, member);

        assertEquals(1500, first.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, first.getOutcome());
        assertEquals(0, retry.getPointsAwarded());
        assertEquals(ProcessingOutcome.DUPLICATE, retry.getOutcome());
        assertTrue(retry.isSkippedAsDuplicate());
    }

    @Test
    void doesNotDoubleAwardPointsWhenAnOlderEventIsResentOutOfOrder() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent eventA = new PaymentEvent("evt-A", "member-1",
                new BigDecimal("1000"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent eventB = new PaymentEvent("evt-B", "member-1",
                new BigDecimal("2000"), false, LocalDate.of(2026, 3, 5));

        // Processing order as it can realistically happen with webhook retries:
        // A arrives, then B arrives, then A is redelivered by the processor.
        engine.processPayment(eventA, member);
        engine.processPayment(eventB, member);
        PointsResult resentA = engine.processPayment(eventA, member);

        assertEquals(0, resentA.getPointsAwarded());
        assertEquals(ProcessingOutcome.DUPLICATE, resentA.getOutcome());
        assertTrue(resentA.isSkippedAsDuplicate());
        assertEquals(3000, member.getPointsForMonth(YearMonth.of(2026, 3)));
    }

    @RepeatedTest(8)
    void awardsPointsAtMostOnceWhenTheSameEventArrivesConcurrently() throws Exception {
        int workerCount = 16;
        RewardsEngine concurrentEngine = new RewardsEngine(
                new PointsCalculator(), new ProcessedEventStore());
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent(
                "evt-concurrent",
                "member-1",
                new BigDecimal("1500"),
                false,
                LocalDate.of(2026, 3, 1));
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return concurrentEngine.processPayment(event, member);
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            long awardedResults = 0;
            for (Future<PointsResult> future : futures) {
                if (future.get(2, TimeUnit.SECONDS).getOutcome() == ProcessingOutcome.AWARDED) {
                    awardedResults++;
                }
            }

            assertEquals(1, awardedResults,
                    "at most one worker may award points for the same eventId");
            assertEquals(1500, member.getPointsForMonth(YearMonth.of(2026, 3)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sharedStorePreventsDuplicateAwardsAcrossEngineInstances() throws Exception {
        ProcessedEventStore store = new ProcessedEventStore();
        RewardsEngine firstEngine = new RewardsEngine(new PointsCalculator(), store);
        RewardsEngine secondEngine = new RewardsEngine(new PointsCalculator(), store);
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-shared", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        List<PointsResult> results = processConcurrently(List.of(
                () -> firstEngine.processPayment(event, member),
                () -> secondEngine.processPayment(event, member)));

        assertEquals(1L, results.stream()
                .filter(result -> result.getOutcome() == ProcessingOutcome.AWARDED)
                .count());
        assertEquals(1L, results.stream()
                .filter(result -> result.getOutcome() == ProcessingOutcome.DUPLICATE)
                .count());
        assertEquals(1500L, results.stream().mapToLong(PointsResult::getPointsAwarded).sum());
        assertEquals(1500L, member.getPointsForMonth(YearMonth.of(2026, 3)));
    }

    @Test
    void remembersCappedEventsAfterLaterEventsAreProcessed() {
        MemberAccount member = new MemberAccount("member-1", 0);
        YearMonth month = YearMonth.of(2026, 3);
        member.addPointsForMonth(month, 100_000L);
        PaymentEvent cappedEvent = new PaymentEvent("evt-capped", "member-1",
                new BigDecimal("50"), false, LocalDate.of(2026, 3, 2));
        PaymentEvent laterEvent = new PaymentEvent("evt-later", "member-1",
                new BigDecimal("75"), false, LocalDate.of(2026, 3, 3));

        PointsResult first = engine.processPayment(cappedEvent, member);
        PointsResult later = engine.processPayment(laterEvent, member);
        PointsResult retry = engine.processPayment(cappedEvent, member);

        assertEquals(ProcessingOutcome.CAPPED, first.getOutcome());
        assertEquals(ProcessingOutcome.CAPPED, later.getOutcome());
        assertEquals(ProcessingOutcome.DUPLICATE, retry.getOutcome());
        assertEquals(0L, retry.getPointsAwarded());
        assertEquals(100_000L, member.getPointsForMonth(month));
    }

    @Test
    void concurrentDistinctEventsRespectTheMonthlyCap() throws Exception {
        ProcessedEventStore store = new ProcessedEventStore();
        RewardsEngine firstEngine = new RewardsEngine(new PointsCalculator(), store);
        RewardsEngine secondEngine = new RewardsEngine(new PointsCalculator(), store);
        MemberAccount member = new MemberAccount("member-1", 0);
        YearMonth month = YearMonth.of(2026, 3);
        member.addPointsForMonth(month, 99_000L);
        PaymentEvent firstEvent = new PaymentEvent("evt-cap-a", "member-1",
                new BigDecimal("800"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent secondEvent = new PaymentEvent("evt-cap-b", "member-1",
                new BigDecimal("800"), false, LocalDate.of(2026, 3, 2));

        List<PointsResult> results = processConcurrently(List.of(
                () -> firstEngine.processPayment(firstEvent, member),
                () -> secondEngine.processPayment(secondEvent, member)));

        // Either event may win the race; the loser gets the partial 200.
        assertEquals(List.of(200L, 800L), results.stream()
                .map(PointsResult::getPointsAwarded).sorted().toList());
        assertTrue(results.stream()
                .allMatch(result -> result.getOutcome() == ProcessingOutcome.AWARDED));
        assertEquals(100_000L, member.getPointsForMonth(month));
    }

    private List<PointsResult> processConcurrently(
            List<Callable<PointsResult>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (Callable<PointsResult> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            List<PointsResult> results = new ArrayList<>();
            for (Future<PointsResult> future : futures) {
                results.add(future.get(2, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
