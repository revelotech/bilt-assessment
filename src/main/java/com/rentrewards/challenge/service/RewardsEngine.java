package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.MemberAccount;
import com.rentrewards.challenge.model.PaymentEvent;
import com.rentrewards.challenge.model.PointsResult;

import java.time.YearMonth;

/**
 * Orchestrates the processing of an incoming payment webhook event:
 *
 *  1. Atomically claim the event, or ignore it if it's a duplicate delivery.
 *  2. Calculate base points (with linked-account multiplier).
 *  3. Apply streak bonus if the member is eligible.
 *  4. Enforce the monthly points cap per member.
 *  5. Record the points.
 */
public class RewardsEngine {

    private static final long MONTHLY_POINTS_CAP = 100_000L;

    private final PointsCalculator pointsCalculator;
    private final ProcessedEventStore processedEventStore;

    public RewardsEngine(PointsCalculator pointsCalculator, ProcessedEventStore processedEventStore) {
        this.pointsCalculator = pointsCalculator;
        this.processedEventStore = processedEventStore;
    }

    public PointsResult processPayment(PaymentEvent event, MemberAccount member) {
        if (!processedEventStore.recordIfNew(event.getEventId())) {
            return new PointsResult(member.getMemberId(), 0, true);
        }

        long basePoints = pointsCalculator.calculateBasePoints(event);
        long pointsWithBonus = pointsCalculator.applyStreakBonusIfEligible(
                basePoints, member.getCurrentStreakMonths());

        YearMonth month = YearMonth.from(event.getPaymentDate());
        long alreadyEarnedThisMonth = member.getPointsForMonth(month);
        long remainingCap = Math.max(0, MONTHLY_POINTS_CAP - alreadyEarnedThisMonth);
        long pointsToAward = Math.min(pointsWithBonus, remainingCap);

        member.addPointsForMonth(month, pointsToAward);

        return new PointsResult(member.getMemberId(), pointsToAward, false);
    }
}
