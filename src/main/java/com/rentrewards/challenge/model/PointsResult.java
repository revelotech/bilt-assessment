package com.rentrewards.challenge.model;

import java.util.Objects;

/**
 * Outcome of processing a single PaymentEvent through the RewardsEngine.
 */
public class PointsResult {

    private final String memberId;
    private final long pointsAwarded;
    private final ProcessingOutcome outcome;

    public PointsResult(String memberId, long pointsAwarded, ProcessingOutcome outcome) {
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.pointsAwarded = pointsAwarded;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    public String getMemberId() {
        return memberId;
    }

    public long getPointsAwarded() {
        return pointsAwarded;
    }

    public ProcessingOutcome getOutcome() {
        return outcome;
    }

    public boolean isSkippedAsDuplicate() {
        return outcome == ProcessingOutcome.DUPLICATE;
    }

    @Override
    public String toString() {
        return "PointsResult{memberId='" + memberId + "', pointsAwarded=" + pointsAwarded
                + ", outcome=" + outcome + "}";
    }
}
