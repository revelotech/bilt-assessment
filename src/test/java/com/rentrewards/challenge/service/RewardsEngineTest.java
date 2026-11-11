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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newFixedThreadPool;
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
        ProcessedEventStore sharedStore = new ProcessedEventStore();
        RewardsEngine[] engines = {
                new RewardsEngine(new PointsCalculator(), sharedStore),
                new RewardsEngine(new PointsCalculator(), sharedStore)
        };
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent(
                "evt-concurrent",
                "member-1",
                new BigDecimal("1500"),
                false,
                LocalDate.of(2026, 3, 1));
        ExecutorService executor = newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                RewardsEngine concurrentEngine = engines[worker % engines.length];
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
    void awardsOnlyTheRemainingMonthlyAllowance() {
        MemberAccount member = new MemberAccount("member-1", 0);
        YearMonth month = YearMonth.of(2026, 3);
        member.addPointsForMonth(month, 99_500);
        PaymentEvent event = new PaymentEvent("evt-partial", "member-1",
                new BigDecimal("1000"), false, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);
        PointsResult retry = engine.processPayment(event, member);

        assertEquals(500, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
        assertEquals(ProcessingOutcome.DUPLICATE, retry.getOutcome());
        assertEquals(100_000, member.getPointsForMonth(month));
    }

    @Test
    void remembersCappedEventsEvenAfterOtherEventsAreProcessed() {
        MemberAccount member = new MemberAccount("member-1", 0);
        YearMonth month = YearMonth.of(2026, 3);
        member.addPointsForMonth(month, 100_000);
        PaymentEvent first = new PaymentEvent("evt-capped", "member-1",
                new BigDecimal("1000"), false, LocalDate.of(2026, 3, 1));
        PaymentEvent second = new PaymentEvent("evt-other", "member-1",
                new BigDecimal("1000"), false, LocalDate.of(2026, 3, 2));

        assertEquals(ProcessingOutcome.CAPPED, engine.processPayment(first, member).getOutcome());
        engine.processPayment(second, member);
        PointsResult retry = engine.processPayment(first, member);

        assertEquals(ProcessingOutcome.DUPLICATE, retry.getOutcome());
        assertEquals(0, retry.getPointsAwarded());
        assertEquals(100_000, member.getPointsForMonth(month));
    }

    @Test
    void allowsRetryAfterCalculationFailsBeforeCreditingPoints() {
        PointsCalculator failingOnce = new PointsCalculator() {
            private boolean firstAttempt = true;

            @Override
            public long calculateBasePoints(PaymentEvent event) {
                if (firstAttempt) {
                    firstAttempt = false;
                    throw new IllegalStateException("Calculation failed");
                }
                return super.calculateBasePoints(event);
            }
        };
        RewardsEngine retryEngine = new RewardsEngine(failingOnce, new ProcessedEventStore());
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-retry", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        assertThrows(IllegalStateException.class, () -> retryEngine.processPayment(event, member));
        assertEquals(0, member.getPointsForMonth(YearMonth.of(2026, 3)));
        PointsResult retry = retryEngine.processPayment(event, member);

        assertEquals(ProcessingOutcome.AWARDED, retry.getOutcome());
        assertEquals(1500, member.getPointsForMonth(YearMonth.of(2026, 3)));
        assertEquals(ProcessingOutcome.DUPLICATE, retryEngine.processPayment(event, member).getOutcome());
    }

    @RepeatedTest(8)
    void keepsMonthlyCapWhenDistinctEventsArriveOnDifferentEnginesConcurrently() throws Exception {
        int workerCount = 16;
        ProcessedEventStore sharedStore = new ProcessedEventStore();
        MemberAccount member = new MemberAccount("member-1", 0);
        YearMonth month = YearMonth.of(2026, 3);
        member.addPointsForMonth(month, 99_500);
        ExecutorService executor = newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<PointsResult>> futures = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                RewardsEngine workerEngine = new RewardsEngine(new PointsCalculator(), sharedStore);
                PaymentEvent event = new PaymentEvent("evt-" + worker, "member-1",
                        new BigDecimal("1000"), false, LocalDate.of(2026, 3, 1));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return workerEngine.processPayment(event, member);
                }));
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            long totalAwarded = 0;
            int awardedResults = 0;
            int cappedResults = 0;
            for (Future<PointsResult> future : futures) {
                PointsResult result = future.get(2, TimeUnit.SECONDS);
                totalAwarded += result.getPointsAwarded();
                if (result.getOutcome() == ProcessingOutcome.AWARDED) {
                    awardedResults++;
                } else if (result.getOutcome() == ProcessingOutcome.CAPPED) {
                    cappedResults++;
                }
            }

            assertEquals(500, totalAwarded);
            assertEquals(1, awardedResults);
            assertEquals(workerCount - 1, cappedResults);
            assertEquals(100_000, member.getPointsForMonth(month));
        } finally {
            executor.shutdownNow();
        }
    }
}
