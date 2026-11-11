package com.rentrewards.challenge.model;

import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory representation of a member's rewards account.
 * In production this would be backed by a database; for this challenge
 * an in-memory model is enough to exercise the business rules.
 */
public class MemberAccount {

    private final String memberId;
    private final int currentStreakMonths;
    private final Map<YearMonth, Long> pointsByMonth = new ConcurrentHashMap<>();
    private final Object pointsLock = new Object();

    public MemberAccount(String memberId, int currentStreakMonths) {
        this.memberId = memberId;
        this.currentStreakMonths = currentStreakMonths;
    }

    public String getMemberId() {
        return memberId;
    }

    public int getCurrentStreakMonths() {
        return currentStreakMonths;
    }

    public long getPointsForMonth(YearMonth month) {
        return pointsByMonth.getOrDefault(month, 0L);
    }

    public void addPointsForMonth(YearMonth month, long points) {
        synchronized (pointsLock) {
            pointsByMonth.merge(month, points, Long::sum);
        }
    }

    /** Atomically credits the available allowance and returns the points awarded. */
    public long awardPointsUpToCap(YearMonth month, long requestedPoints, long monthlyCap) {
        synchronized (pointsLock) {
            long alreadyEarned = getPointsForMonth(month);
            long remainingCap = Math.max(0, monthlyCap - alreadyEarned);
            long pointsToAward = Math.min(requestedPoints, remainingCap);
            pointsByMonth.merge(month, pointsToAward, Long::sum);
            return pointsToAward;
        }
    }
}
