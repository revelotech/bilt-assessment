package com.rentrewards.challenge.model;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal in-memory representation of a member's rewards account.
 * In production this would be backed by a database; for this challenge
 * an in-memory model is enough to exercise the business rules.
 */
public class MemberAccount {

    private final String memberId;
    private int currentStreakMonths;
    private final Map<YearMonth, Long> pointsByMonth = new HashMap<>();

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
        pointsByMonth.merge(month, points, Long::sum);
    }
}
