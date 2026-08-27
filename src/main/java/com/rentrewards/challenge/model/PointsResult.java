package com.rentrewards.challenge.model;

/**
 * Outcome of processing a single PaymentEvent through the RewardsEngine.
 */
public class PointsResult {

    private final String memberId;
    private final long pointsAwarded;
    private final boolean skippedAsDuplicate;

    public PointsResult(String memberId, long pointsAwarded, boolean skippedAsDuplicate) {
        this.memberId = memberId;
        this.pointsAwarded = pointsAwarded;
        this.skippedAsDuplicate = skippedAsDuplicate;
    }

    public String getMemberId() {
        return memberId;
    }

    public long getPointsAwarded() {
        return pointsAwarded;
    }

    public boolean isSkippedAsDuplicate() {
        return skippedAsDuplicate;
    }

    @Override
    public String toString() {
        return "PointsResult{memberId='" + memberId + "', pointsAwarded=" + pointsAwarded
                + ", skippedAsDuplicate=" + skippedAsDuplicate + "}";
    }
}
