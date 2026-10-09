# Implementation Plan

This document describes the step-by-step implementation details, code changes, and atomic operations applied to resolve the idempotency issue and fix the dashboard states.

---

## 1. Overview

The changes are divided into two main components:
- **Backend (Java):** Refactor `ProcessedEventStore` and `RewardsEngine` for concurrent, atomic deduplication.
- **Frontend (JavaScript):** Map outcomes in `dashboard.js` according to `result.outcome`.

---

## 2. Step 1: Backend Implementation (Java)

### A. `src/main/java/com/rentrewards/challenge/service/ProcessedEventStore.java`

Replace the single `lastProcessedEventId` field with a `Set<String>` backed by `ConcurrentHashMap.newKeySet()`, providing `tryRecord(String eventId)` for atomic check-and-insert:

```java
package com.rentrewards.challenge.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessedEventStore {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * @return true if this eventId has already been processed before.
     */
    public boolean isDuplicate(String eventId) {
        return processedEventIds.contains(eventId);
    }

    public void markProcessed(String eventId) {
        processedEventIds.add(eventId);
    }

    /**
     * Atomically records the eventId if it has not been processed yet.
     *
     * @return true if this is the first time the event is recorded (not a duplicate),
     *         false if it was already recorded previously.
     */
    public boolean tryRecord(String eventId) {
        return processedEventIds.add(eventId);
    }
}
```

---

### B. `src/main/java/com/rentrewards/challenge/service/RewardsEngine.java`

Update `processPayment` to atomically claim the event via `tryRecord` before executing calculation logic and state updates:

```java
    public PointsResult processPayment(PaymentEvent event, MemberAccount member) {
        if (!processedEventStore.tryRecord(event.getEventId())) {
            return new PointsResult(member.getMemberId(), 0, ProcessingOutcome.DUPLICATE);
        }

        long basePoints = pointsCalculator.calculateBasePoints(event);
        long pointsWithBonus = pointsCalculator.applyStreakBonusIfEligible(
                basePoints, member.getCurrentStreakMonths());

        YearMonth month = YearMonth.from(event.getPaymentDate());
        long alreadyEarnedThisMonth = member.getPointsForMonth(month);
        long remainingCap = Math.max(0, MONTHLY_POINTS_CAP - alreadyEarnedThisMonth);
        long pointsToAward = Math.min(pointsWithBonus, remainingCap);

        member.addPointsForMonth(month, pointsToAward);

        ProcessingOutcome outcome = pointsToAward == 0
                ? ProcessingOutcome.CAPPED
                : ProcessingOutcome.AWARDED;
        return new PointsResult(member.getMemberId(), pointsToAward, outcome);
    }
```

---

## 3. Step 2: Frontend Implementation (JavaScript)

### `web/dashboard.js`

Update `buildViewModel` to branch based on `result.outcome`:

```javascript
export function buildViewModel(result, member) {
  const progressPercent = Math.min(
    100,
    (member.pointsThisMonth / member.monthlyCap) * 100,
  );

  let title;
  let description;
  let tone;

  switch (result.outcome) {
    case "DUPLICATE":
      title = "Duplicate event skipped";
      description = "This payment event was already processed.";
      tone = "neutral";
      break;
    case "CAPPED":
      title = "Monthly cap reached";
      description = "You have reached your monthly points cap.";
      tone = "warning";
      break;
    case "AWARDED":
    default:
      title = `${numberFormatter.format(result.pointsAwarded)} points credited`;
      description = "Your rent payment was processed successfully.";
      tone = "success";
      break;
  }

  return {
    title,
    description,
    tone,
    progressPercent,
  };
}
```

---

## 4. Acceptance Criteria

1. `mvn test`: 100% tests pass including concurrent worker stress tests.
2. `npm run test:web`: 100% tests pass.
3. No regressions on base points, streak bonuses, or monthly capping rules.

