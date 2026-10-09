package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.PaymentEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointsCalculatorTest {

    @Test
    void calculatesOnePointPerDollarForAnUnlinkedAccount() {
        PointsCalculator calculator = new PointsCalculator();
        PaymentEvent event = new PaymentEvent("evt-1", "member-1",
                new BigDecimal("1500"), false, LocalDate.of(2026, 3, 1));

        long points = calculator.calculateBasePoints(event);

        assertEquals(1500, points);
    }
}
