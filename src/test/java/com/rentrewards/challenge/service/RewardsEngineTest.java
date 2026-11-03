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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void reportsDuplicateRatherThanCappedWhenACappedEventIsResent() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent fillsCap = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("150000"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent capped = new PaymentEvent("evt-2", "member-1",
                new BigDecimal("50"), false, LocalDate.of(2026, 3, 2));

        engine.processPayment(fillsCap, member);
        assertEquals(ProcessingOutcome.CAPPED, engine.processPayment(capped, member).getOutcome());

        PointsResult resent = engine.processPayment(capped, member);

        assertEquals(ProcessingOutcome.DUPLICATE, resent.getOutcome());
        assertEquals(0, resent.getPointsAwarded());
    }

    @Test
    void ignoresEveryRetryWhenManyEventsAreRedeliveredInAnyOrder() {
        MemberAccount member = new MemberAccount("member-1", 0);
        List<PaymentEvent> events = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            events.add(new PaymentEvent("evt-" + i, "member-1",
                    new BigDecimal(100 * i), false, LocalDate.of(2026, 3, i)));
        }
        events.forEach(e -> assertEquals(ProcessingOutcome.AWARDED,
                engine.processPayment(e, member).getOutcome()));

        // Redeliver in reverse order, then the middle ones again.
        for (int i = events.size() - 1; i >= 0; i--) {
            assertEquals(ProcessingOutcome.DUPLICATE,
                    engine.processPayment(events.get(i), member).getOutcome());
        }
        assertEquals(ProcessingOutcome.DUPLICATE, engine.processPayment(events.get(2), member).getOutcome());

        // 100 + 200 + 300 + 400 + 500
        assertEquals(1500, member.getPointsForMonth(YearMonth.of(2026, 3)));
    }

    @Test
    void streakAndMultiplierStillApplyBeforeTheMonthlyCap() {
        MemberAccount member = new MemberAccount("member-1", 6);
        PaymentEvent nearCap = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("98000"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent bonusEvent = new PaymentEvent("evt-2", "member-1",
                new BigDecimal("2000"), true, LocalDate.of(2026, 3, 2));

        // 98000 + 10% streak bonus = 107800 -> capped at 100000 on the first event.
        PointsResult first = engine.processPayment(nearCap, member);
        assertEquals(100_000, first.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, first.getOutcome());

        PointsResult second = engine.processPayment(bonusEvent, member);
        assertEquals(ProcessingOutcome.CAPPED, second.getOutcome());

        // The cap is per calendar month: April starts fresh with 2000 * 2 * 1.1.
        PaymentEvent april = new PaymentEvent("evt-3", "member-1",
                new BigDecimal("2000"), true, LocalDate.of(2026, 4, 1));
        PointsResult aprilResult = engine.processPayment(april, member);
        assertEquals(4400, aprilResult.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, aprilResult.getOutcome());
    }

    @Test
    void releasesTheClaimWhenProcessingFailsSoARetryIsNotLost() {
        ProcessedEventStore store = new ProcessedEventStore();
        RewardsEngine failingEngine = new RewardsEngine(new PointsCalculator(), store);
        MemberAccount member = new MemberAccount("member-1", 0);
        // Too large to fit in a long: the calculator throws.
        PaymentEvent event = new PaymentEvent("evt-boom", "member-1",
                new BigDecimal("1e30"), false, LocalDate.of(2026, 3, 1));

        assertThrows(ArithmeticException.class, () -> failingEngine.processPayment(event, member));

        assertFalse(store.isDuplicate("evt-boom"));
        assertEquals(0, member.getPointsForMonth(YearMonth.of(2026, 3)));
    }

    @RepeatedTest(8)
    void awardsEachEventExactlyOnceWhenManyEventsAreDeliveredConcurrentlyAndRepeatedly()
            throws Exception {
        int eventCount = 20;
        int deliveriesPerEvent = 4;
        RewardsEngine concurrentEngine = new RewardsEngine(
                new PointsCalculator(), new ProcessedEventStore());
        MemberAccount member = new MemberAccount("member-1", 0);
        List<PaymentEvent> deliveries = new ArrayList<>();
        for (int d = 0; d < deliveriesPerEvent; d++) {
            for (int i = 0; i < eventCount; i++) {
                deliveries.add(new PaymentEvent("evt-" + i, "member-1",
                        new BigDecimal("100"), false, LocalDate.of(2026, 3, 1)));
            }
        }
        ExecutorService executor = Executors.newFixedThreadPool(deliveries.size());
        CountDownLatch ready = new CountDownLatch(deliveries.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (PaymentEvent delivery : deliveries) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return concurrentEngine.processPayment(delivery, member);
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            long awarded = 0;
            long duplicates = 0;
            for (Future<PointsResult> future : futures) {
                ProcessingOutcome outcome = future.get(2, TimeUnit.SECONDS).getOutcome();
                if (outcome == ProcessingOutcome.AWARDED) {
                    awarded++;
                } else if (outcome == ProcessingOutcome.DUPLICATE) {
                    duplicates++;
                }
            }

            assertEquals(eventCount, awarded);
            assertEquals((long) eventCount * (deliveriesPerEvent - 1), duplicates);
            assertEquals(eventCount * 100L, member.getPointsForMonth(YearMonth.of(2026, 3)));
        } finally {
            executor.shutdownNow();
        }
    }

    @RepeatedTest(8)
    void neverExceedsTheMonthlyCapWhenDistinctEventsArriveConcurrently() throws Exception {
        int workerCount = 16;
        RewardsEngine concurrentEngine = new RewardsEngine(
                new PointsCalculator(), new ProcessedEventStore());
        MemberAccount member = new MemberAccount("member-1", 0);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                // 16 x 10,000 = 160,000 requested; only 100,000 may be credited.
                PaymentEvent event = new PaymentEvent("evt-" + worker, "member-1",
                        new BigDecimal("10000"), false, LocalDate.of(2026, 3, 1));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return concurrentEngine.processPayment(event, member);
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            long totalAwarded = 0;
            for (Future<PointsResult> future : futures) {
                PointsResult result = future.get(2, TimeUnit.SECONDS);
                assertFalse(result.isSkippedAsDuplicate());
                totalAwarded += result.getPointsAwarded();
            }

            assertEquals(100_000, totalAwarded);
            assertEquals(100_000, member.getPointsForMonth(YearMonth.of(2026, 3)));
        } finally {
            executor.shutdownNow();
        }
    }
}
