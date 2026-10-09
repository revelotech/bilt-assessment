package com.rentrewards.challenge.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCoverageTest {

    @Test
    void testPaymentEventModel() {
        LocalDate date = LocalDate.of(2026, 3, 15);
        PaymentEvent event = new PaymentEvent("evt-10", "mem-20", new BigDecimal("1200"), true, date);

        assertEquals("evt-10", event.getEventId());
        assertEquals("mem-20", event.getMemberId());
        assertEquals(new BigDecimal("1200"), event.getAmount());
        assertTrue(event.isPaidWithLinkedAccount());
        assertEquals(date, event.getPaymentDate());

        assertThrows(NullPointerException.class, () -> new PaymentEvent(null, "m", BigDecimal.ONE, true, date));
        assertThrows(NullPointerException.class, () -> new PaymentEvent("e", null, BigDecimal.ONE, true, date));
        assertThrows(NullPointerException.class, () -> new PaymentEvent("e", "m", null, true, date));
        assertThrows(NullPointerException.class, () -> new PaymentEvent("e", "m", BigDecimal.ONE, true, null));
    }

    @Test
    void testMemberAccountModel() {
        MemberAccount member = new MemberAccount("mem-1", 5);

        assertEquals("mem-1", member.getMemberId());
        assertEquals(5, member.getCurrentStreakMonths());

        YearMonth march = YearMonth.of(2026, 3);
        YearMonth april = YearMonth.of(2026, 4);

        assertEquals(0, member.getPointsForMonth(march));

        member.addPointsForMonth(march, 1500);
        assertEquals(1500, member.getPointsForMonth(march));

        member.addPointsForMonth(march, 500);
        assertEquals(2000, member.getPointsForMonth(march));
        assertEquals(0, member.getPointsForMonth(april));
    }

    @Test
    void testPointsResultModel() {
        PointsResult result = new PointsResult("mem-1", 2500, ProcessingOutcome.AWARDED);

        assertEquals("mem-1", result.getMemberId());
        assertEquals(2500, result.getPointsAwarded());
        assertEquals(ProcessingOutcome.AWARDED, result.getOutcome());
        assertFalse(result.isSkippedAsDuplicate());
        assertNotNull(result.toString());

        PointsResult duplicate = new PointsResult("mem-1", 0, ProcessingOutcome.DUPLICATE);
        assertTrue(duplicate.isSkippedAsDuplicate());

        assertThrows(NullPointerException.class, () -> new PointsResult(null, 100, ProcessingOutcome.AWARDED));
        assertThrows(NullPointerException.class, () -> new PointsResult("mem-1", 100, null));
    }

    @Test
    void testProcessingOutcomeEnum() {
        assertEquals(3, ProcessingOutcome.values().length);
        assertEquals(ProcessingOutcome.AWARDED, ProcessingOutcome.valueOf("AWARDED"));
        assertEquals(ProcessingOutcome.DUPLICATE, ProcessingOutcome.valueOf("DUPLICATE"));
        assertEquals(ProcessingOutcome.CAPPED, ProcessingOutcome.valueOf("CAPPED"));
    }
}
