package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.MemberAccount;
import com.rentrewards.challenge.model.PaymentEvent;
import com.rentrewards.challenge.model.PointsResult;
import com.rentrewards.challenge.model.ProcessingOutcome;

import java.time.YearMonth;

/**
 * Orchestrates the processing of an incoming payment webhook event:
 *
 *  1. Atomically claim the eventId; ignore the event if it was already claimed.
 *  2. Calculate base points (with linked-account multiplier).
 *  3. Apply streak bonus if the member is eligible.
 *  4. Enforce the monthly points cap per member.
 *  5. Record the points (releasing the claim if processing fails).
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
        // Claim before doing any work: only one delivery of an eventId may win.
        if (!processedEventStore.tryClaim(event.getEventId())) {
            return new PointsResult(member.getMemberId(), 0, ProcessingOutcome.DUPLICATE);
        }

        long pointsToAward;
        try {
            long basePoints = pointsCalculator.calculateBasePoints(event);
            long pointsWithBonus = pointsCalculator.applyStreakBonusIfEligible(
                    basePoints, member.getCurrentStreakMonths());

            YearMonth month = YearMonth.from(event.getPaymentDate());
            // Read-check-write of the monthly total must be atomic per member,
            // otherwise distinct concurrent events could exceed the cap.
            synchronized (member) {
                long alreadyEarnedThisMonth = member.getPointsForMonth(month);
                long remainingCap = Math.max(0, MONTHLY_POINTS_CAP - alreadyEarnedThisMonth);
                pointsToAward = Math.min(pointsWithBonus, remainingCap);
                member.addPointsForMonth(month, pointsToAward);
            }
        } catch (RuntimeException e) {
            // Nothing was credited; let a future retry process this event.
            processedEventStore.release(event.getEventId());
            throw e;
        }

        ProcessingOutcome outcome = pointsToAward == 0
                ? ProcessingOutcome.CAPPED
                : ProcessingOutcome.AWARDED;
        return new PointsResult(member.getMemberId(), pointsToAward, outcome);
    }
}
