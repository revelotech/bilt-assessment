package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.MemberAccount;
import com.rentrewards.challenge.model.PaymentEvent;
import com.rentrewards.challenge.model.PointsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        assertFalse(result.isSkippedAsDuplicate());
    }

    @Test
    void appliesLinkedAccountMultiplier() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), true, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);

        assertEquals(3000, result.getPointsAwarded());
    }

    @Test
    void appliesStreakBonusWhenEligible() {
        MemberAccount member = new MemberAccount("member-1", 6);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("2000"), true, LocalDate.of(2026, 3, 1));

        // base = 2000 * 2 (linked) = 4000; +10% streak bonus = 4400
        PointsResult result = engine.processPayment(event, member);

        assertEquals(4400, result.getPointsAwarded());
    }

    @Test
    void enforcesMonthlyPointsCap() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("150000"), false, LocalDate.of(2026, 3, 1));

        PointsResult result = engine.processPayment(event, member);

        assertEquals(100_000, result.getPointsAwarded());
    }

    @Test
    void doesNotDoubleAwardPointsWhenSameWebhookEventIsResent() {
        MemberAccount member = new MemberAccount("member-1", 0);
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        PointsResult first = engine.processPayment(event, member);
        PointsResult retry = engine.processPayment(event, member);

        assertEquals(1500, first.getPointsAwarded());
        assertEquals(0, retry.getPointsAwarded());
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
        assertTrue(resentA.isSkippedAsDuplicate());
    }
}
