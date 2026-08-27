package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.PaymentEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encapsulates the point calculation business rules:
 *
 *  - Base rate: 1 point per $1 paid.
 *  - Linked account multiplier: payments made with a linked bank account
 *    earn 2x points.
 *  - Streak bonus: members with an active 6+ month on-time payment streak
 *    earn an additional 10% bonus on top of the (already multiplied) points.
 *
 * This class is considered stable / correct for the purposes of this
 * challenge - the bug you are looking for is NOT here.
 */
public class PointsCalculator {

    private static final BigDecimal LINKED_ACCOUNT_MULTIPLIER = new BigDecimal("2");
    private static final BigDecimal STREAK_BONUS_RATE = new BigDecimal("0.10");
    private static final int MIN_STREAK_MONTHS_FOR_BONUS = 6;

    public long calculateBasePoints(PaymentEvent event) {
        BigDecimal points = event.getAmount();
        if (event.isPaidWithLinkedAccount()) {
            points = points.multiply(LINKED_ACCOUNT_MULTIPLIER);
        }
        return points.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public long applyStreakBonusIfEligible(long basePoints, int currentStreakMonths) {
        if (currentStreakMonths < MIN_STREAK_MONTHS_FOR_BONUS) {
            return basePoints;
        }
        BigDecimal bonus = new BigDecimal(basePoints).multiply(STREAK_BONUS_RATE);
        return basePoints + bonus.setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
