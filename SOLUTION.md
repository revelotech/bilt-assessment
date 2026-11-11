# Summary of Changes

## Frontend

The dashboard was always showing a credit as successful, even when the payment was a duplicate or blocked by the monthly cap.

To fix this, I updated the UI to use `result.outcome` as the source of truth for the title, description, and visual status:

| Outcome | Display | Tone |
| --- | --- | --- |
| `AWARDED` | Number of points credited | Success |
| `DUPLICATE` | Duplicate event ignored; no additional points awarded | Neutral |
| `CAPPED` | Monthly cap reached; no points awarded | Warning |

I isolated this logic in `buildStatusView()`, keeping status handling separate from progress calculations and DOM rendering while preserving the existing styles.

The dashboard no longer relies on the balance or progress bar to determine the status. If a partial award reaches the monthly cap, it still returns `AWARDED`, even when progress hits 100%.

Also, unknown outcomes now throw an error instead of falling back to a generic success message.

---

## Backend

### 1. Event History and Idempotency

The previous store only kept track of the last processed event ID. With a sequence like `A -> B -> A`, the system would forget about A and award points for it again.

On top of that, checking for duplicates separately from inserting the event created a race condition between concurrent workers.

* **What changed:** I replaced the single ID field with a thread-safe `Set` using `ConcurrentHashMap.newKeySet()`. The `registerIfAbsent()` method now relies on the atomic result of `add()`: only the worker that successfully inserts the ID gets `true` and continues processing. Everyone else gets `DUPLICATE` with zero points.
* **Validation order:** Duplicate detection now happens **before** points calculation and cap enforcement. Retrying an event that previously hit the cap returns `DUPLICATE` instead of being treated as a new `CAPPED` event. If the calculation fails before points are awarded, the event registration is rolled back so it can be retried.

### 2. Monthly Cap Protection

Preventing duplicates wasn't enough. Two different events could still read the remaining balance at the same time and push the account over the monthly limit.

* **What changed:** `MemberAccount.awardPointsUpToCap()` now checks the balance, calculates the allowed amount, and updates the account state within a single private lock. The `addPointsForMonth` method uses the same locking mechanism.
* For example, if a member has 99,500 points and receives an event worth 1,000 points, they get 500 points with an `AWARDED` status. The next event, with no remaining capacity, returns `CAPPED`.

### 3. Separation of Responsibilities

The flow is now cleaner: `processPayment()` orchestrates the entire process (registration, calculation, awarding points, and returning the result), while `calculatePointsWithBonus` focuses strictly on the business rules.

The existing rules remain unchanged: 1 point per dollar, a 2x multiplier for linked accounts, and a 10% bonus for streaks of 6+ months in the payment month.

Everything is still kept in memory, as originally planned.

---

## Validation

To run the tests locally:

```sh
mvn test
npm run test:web
```

* **Backend:** All 26 tests passed, including heavy concurrency scenarios, out-of-order retries, concurrent processing across multiple engines sharing the same store, partial awards, and failure recovery.
* **Frontend:** All 5 tests passed, covering all three outcomes, monthly cap behavior with partial awards, and rejection of unknown states.