# Solution Plan: Idempotency Fix and Dashboard State Alignment

This document outlines the high-level technical strategy to resolve the backend idempotency bug and align the frontend dashboard with event processing outcomes.

---

## 1. Objectives

1. **Ensure Backend Idempotency (Java):**
   * Guarantee that any `eventId` awards points at most once.
   * Accurately handle out-of-order deliveries and duplicate retries.
   * Safely handle concurrent deliveries across worker threads without race conditions.
   * Ensure 100% pass rate across the Maven test suite (`mvn test`).
2. **Align Dashboard UI with Real Processing Outcomes (JavaScript):**
   * Map `AWARDED`, `DUPLICATE`, and `CAPPED` outcomes to the appropriate ViewModel title, description, and visual tone.
   * Ensure 100% pass rate across the web test suite (`npm run test:web`).
3. **Preserve All Existing Business Rules:**
   * Keep base calculation rules ($1 = 1 point), linked account bonus (2x), streak bonus (+10% for 6+ months), and the monthly cap (100,000 points) completely intact.

---

## 2. Phase 1: Backend Solution (Java)

### Problem
`ProcessedEventStore` retained only the latest processed ID (`lastProcessedEventId`), causing out-of-order retries to bypass deduplication, while lacking atomic primitives for multithreaded environments.

### Solution Strategy
* **Concurrent In-Memory Storage:**
  Use a concurrent set backed by `ConcurrentHashMap.newKeySet()` inside `ProcessedEventStore`.
* **Atomic Deduplication Primitive:**
  Introduce `tryRecord(String eventId)` leveraging `Set.add(eventId)`. `Set.add()` is atomic in concurrent sets: it returns `true` if the element was absent and added, or `false` if it was already present.
* **Compatibility Preservation:**
  Keep the existing `isDuplicate(String eventId)` and `markProcessed(String eventId)` methods intact to ensure backward compatibility.
* **Orchestration in `RewardsEngine`:**
  Atomically invoke `processedEventStore.tryRecord(event.getEventId())` before modifying member points. If it returns `false`, immediately return `PointsResult` with outcome `DUPLICATE`.

---

## 3. Phase 2: Frontend Solution (JavaScript)

### Problem
`buildViewModel(result, member)` in `web/dashboard.js` hardcoded a success tone and credited title regardless of the actual outcome.

### Solution Strategy
Evaluate `result.outcome` in `buildViewModel`:
1. **`AWARDED`:**
   * `title`: `"${numberFormatter.format(result.pointsAwarded)} points credited"`
   * `description`: `"Your rent payment was processed successfully."`
   * `tone`: `"success"`
2. **`DUPLICATE`:**
   * `title`: `"Duplicate event skipped"`
   * `description`: `"This payment event was already processed."`
   * `tone`: `"neutral"`
3. **`CAPPED`:**
   * `title`: `"Monthly cap reached"`
   * `description`: `"You have reached your monthly points cap."`
   * `tone`: `"warning"`

---

## 4. Phase 3: Verification & Acceptance Criteria

1. **Backend Tests:**
   * Execute `mvn test`.
   * Validate out-of-order redeliveries (`doesNotDoubleAwardPointsWhenAnOlderEventIsResentOutOfOrder`).
   * Validate concurrent redeliveries (`awardsPointsAtMostOnceWhenTheSameEventArrivesConcurrently`).
2. **Frontend Tests:**
   * Execute `npm run test:web`.
   * Validate all three outcome representations (`AWARDED`, `DUPLICATE`, and `CAPPED`).

