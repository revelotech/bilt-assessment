package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.PaymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointsCalculatorTest {

    private PointsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PointsCalculator();
    }

    @Test
    void calculateBasePointsWithoutLinkedAccount() {
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1250.75"), false, LocalDate.of(2026, 3, 1));
        long points = calculator.calculateBasePoints(event);
        assertEquals(1250, points);
    }

    @Test
    void calculateBasePointsWithLinkedAccount() {
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1250.75"), true, LocalDate.of(2026, 3, 1));
        long points = calculator.calculateBasePoints(event);
        // 1250.75 * 2 = 2501.50 -> truncated DOWN to 2501
        assertEquals(2501, points);
    }

    @Test
    void applyStreakBonusBelowThresholdDoesNotApplyBonus() {
        long result = calculator.applyStreakBonusIfEligible(1000, 5);
        assertEquals(1000, result);
    }

    @Test
    void applyStreakBonusAtThresholdAppliesTenPercent() {
        long result = calculator.applyStreakBonusIfEligible(1000, 6);
        assertEquals(1100, result);
    }

    @Test
    void applyStreakBonusAboveThresholdAppliesTenPercent() {
        long result = calculator.applyStreakBonusIfEligible(2500, 12);
        assertEquals(2750, result);
    }

    @Test
    void applyStreakBonusRoundsDownFractionalBonus() {
        // 1005 * 0.10 = 100.5 -> truncated to 100 -> 1005 + 100 = 1105
        long result = calculator.applyStreakBonusIfEligible(1005, 6);
        assertEquals(1105, result);
    }
}
