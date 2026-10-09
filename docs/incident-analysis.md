# Incident Analysis and Technical Diagnosis

This document details the root-cause analysis of the double-crediting bug and the dashboard status inconsistency on the **RentRewards** platform.

---

## 1. Business Context and Architecture

RentRewards enables members to earn reward points on housing payments (rent and mortgage). Payments are handled by an external payment processor, which delivers asynchronous notifications via **webhook events** to the backend service (`RewardsEngine`).

### Delivery Guarantee: *At-Least-Once Delivery*
The payment processor guarantees **at-least-once delivery**. In real-world distributed systems and network environments, this implies that the same payment event (`PaymentEvent` with a unique `eventId`) can be delivered:
1. **Multiple times**, caused by automated retries after network timeouts or delayed HTTP acknowledgments.
2. **Out of chronological order**, interleaving with newer events.
3. **Concurrently across multiple workers or threads**, arriving simultaneously.

---

## 2. Identified Issues and Root Causes

---

### Issue A: Idempotency Failure & Double Points Awarding (Backend - Java)

#### Problem Report
Members occasionally receive reward points twice after webhook retries, specifically when events arrive out of order or are processed concurrently across different worker threads.

#### Technical Diagnosis and Root Causes

1. **State Loss on Interleaved Events (Out-of-Order Deliveries):**
   * **Location:** `src/main/java/com/rentrewards/challenge/service/ProcessedEventStore.java`
   * **Defect:** The class only tracked a single event ID in memory:
     ```java
     private String lastProcessedEventId;

     public boolean isDuplicate(String eventId) {
         return eventId.equals(lastProcessedEventId);
     }
     ```
   * **Impact:** It only remembered the **most recent** event processed. If event $A$ arrives, followed by event $B$, and then an automated redelivery of event $A$ arrives, `eventId.equals(lastProcessedEventId)` compares $A$ against $B$, evaluates to `false`, and wrongly credits points for event $A$ a second time.

2. **Race Condition / Lack of Atomicity (Concurrent Deliveries):**
   * **Location:** `RewardsEngine.java` and `ProcessedEventStore.java`
   * **Defect:** The processing flow in `RewardsEngine.processPayment(...)` followed an un-synchronized **Check-Then-Act** pattern:
     ```java
     if (processedEventStore.isDuplicate(event.getEventId())) {
         return new PointsResult(..., DUPLICATE);
     }
     // ... calculation and points addition ...
     processedEventStore.markProcessed(event.getEventId());
     ```
   * **Impact:** 
     * When multiple threads or workers receive the same `eventId` concurrently, both threads invoke `isDuplicate(...)` before either completes `markProcessed(...)`.
     * Both evaluate the event as non-duplicate, calculate points, and mutate the `MemberAccount` state.
     * `ProcessedEventStore` lacked synchronization (`synchronized`, `Lock`, or atomic data structures).

---

### Issue B: UI Dashboard Status Misrepresentation (Frontend - JavaScript)

#### Problem Report
The rewards dashboard always announces that points were successfully credited, even when an event was skipped as a duplicate or when the member reached their monthly cap.

#### Technical Diagnosis and Root Cause

* **Location:** `web/dashboard.js`
* **Defect:** In `buildViewModel(result, member)`:
  ```javascript
  export function buildViewModel(result, member) {
    return {
      title: `${numberFormatter.format(result.pointsAwarded)} points credited`,
      description: "Your rent payment was processed successfully.",
      tone: "success",
      progressPercent,
    };
  }
  ```
* **Impact:** 
  * The implementation completely ignored the `result.outcome` attribute (`AWARDED`, `DUPLICATE`, or `CAPPED`).
  * When an event was skipped as duplicate (`DUPLICATE`), the UI showed the confusing message `"0 points credited"` in green success style (`tone: "success"`), rather than `"Duplicate event skipped"` in neutral style (`tone: "neutral"`).
  * When a member reached their 100,000 monthly point cap (`CAPPED`), it also displayed `"0 points credited"` as success, instead of warning `"Monthly cap reached"` (`tone: "warning"`).

---

## 3. Mitigation and Solution Strategy

### Backend:
1. **Thread-Safe Historical Storage:** Replace the single `lastProcessedEventId` field in `ProcessedEventStore` with a thread-safe set holding all processed event IDs (e.g., `ConcurrentHashMap.newKeySet()`).
2. **Atomic Check-and-Add Operation:** Provide an atomic registration method (e.g., `tryRecord(String eventId)`) backed by `Set.add()`. This guarantees that in high-concurrency environments, exactly one thread successfully claims the event and awards points, while all competing threads immediately receive `DUPLICATE`.

### Frontend:
1. **Outcome-Driven View Model:** Refactor `buildViewModel` in `web/dashboard.js` to evaluate `result.outcome`:
   * **`AWARDED`:** Title `"${pointsAwarded} points credited"`, tone `success`.
   * **`DUPLICATE`:** Title `"Duplicate event skipped"`, tone `neutral`.
   * **`CAPPED`:** Title `"Monthly cap reached"`, tone `warning`.

