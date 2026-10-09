# Test Plan and Verification Strategy

This document details the test strategy, coverage matrix, and test results for verifying the backend (Java) and frontend (JavaScript) fixes in **RentRewards**.

---

## 1. Scope and Test Levels

The verification strategy encompasses three levels:
1. **Model & Unit Tests (Java / JUnit 5):** Validates entities (`MemberAccount`, `PaymentEvent`, `PointsResult`, `ProcessingOutcome`), deduplication store (`ProcessedEventStore`), and calculation rules (`PointsCalculator`).
2. **Integration & Concurrency Tests (Java / JUnit 5):** Validates out-of-order deliveries and multithreaded stress testing in `RewardsEngineTest`.
3. **Frontend Tests (Node.js Test Runner):** Validates viewmodel generation in `web/dashboard.test.js`.

---

## 2. Test Coverage Matrix - Backend (Java)

| Test Class | Test Case | Scenario / Purpose | Expected Result |
| :--- | :--- | :--- | :--- |
| **`RewardsEngineTest`** | `awardsOnePointPerDollarByDefault` | Standard $1,500 payment | 1,500 points awarded (`AWARDED`) |
| **`RewardsEngineTest`** | `appliesLinkedAccountMultiplier` | Linked account payment | 2x multiplier (3,000 points, `AWARDED`) |
| **`RewardsEngineTest`** | `appliesStreakBonusWhenEligible` | 6+ months streak | 2x + 10% bonus (4,400 points, `AWARDED`) |
| **`RewardsEngineTest`** | `enforcesMonthlyPointsCap` | Payment exceeds monthly limit | Cap enforced at 100,000 points |
| **`RewardsEngineTest`** | `reportsCappedWhenALaterEventCannotAwardMorePoints` | Subsequent event after reaching cap | 0 points awarded (`CAPPED`) |
| **`RewardsEngineTest`** | `doesNotDoubleAwardPointsWhenSameWebhookEventIsResent` | Immediate retry of same event | Second attempt receives `DUPLICATE` |
| **`RewardsEngineTest`** | `doesNotDoubleAwardPointsWhenAnOlderEventIsResentOutOfOrder` | Interleaved events (A -> B -> A) | Redelivered A is recognized as `DUPLICATE` |
| **`RewardsEngineTest`** | `awardsPointsAtMostOnceWhenTheSameEventArrivesConcurrently` | 16 concurrent workers competing simultaneously | Exactly 1 worker awards points, 15 receive `DUPLICATE` |
| **`ProcessedEventStoreTest`** | `isDuplicateReturnsFalseForUnseenEvent` | Query unseen event | Returns `false` |
| **`ProcessedEventStoreTest`** | `markProcessedMakesEventADuplicate` | Mark and query event | Returns `true` |
| **`ProcessedEventStoreTest`** | `remembersMultipleDistinctEventsOutOfOrder` | Interleaved event IDs | All previously stored IDs are recognized |
| **`ProcessedEventStoreTest`** | `tryRecordReturnsTrueForFirstAttemptAndFalseSubsequently` | Atomic insert check | Returns `true` on first call, `false` on retry |
| **`ProcessedEventStoreTest`** | `tryRecordIsThreadSafeUnderHighConcurrency` | 32 parallel threads invoking `tryRecord` | Exactly 1 returns `true`, 31 return `false` |
| **`PointsCalculatorTest`** | `calculateBasePointsWithoutLinkedAccount` | Unlinked calculation | Correct truncated base points |
| **`PointsCalculatorTest`** | `calculateBasePointsWithLinkedAccount` | Linked 2x calculation | Correct truncated multiplied points |
| **`PointsCalculatorTest`** | `applyStreakBonusBelowThresholdDoesNotApplyBonus` | 5 months streak | Base points unchanged |
| **`PointsCalculatorTest`** | `applyStreakBonusAtThresholdAppliesTenPercent` | 6 months streak | 10% bonus applied |
| **`PointsCalculatorTest`** | `applyStreakBonusAboveThresholdAppliesTenPercent` | 12 months streak | 10% bonus applied |
| **`PointsCalculatorTest`** | `applyStreakBonusRoundsDownFractionalBonus` | Fractional bonus rounding | Rounds down fractional point amounts |
| **`ModelCoverageTest`** | `testPaymentEventModel` | Model construction & null checks | Validates immutability and preconditions |
| **`ModelCoverageTest`** | `testMemberAccountModel` | Monthly points aggregation | Validates thread-safe monthly accrual |
| **`ModelCoverageTest`** | `testPointsResultModel` | Result accessors and toString | Validates getters and helper methods |
| **`ModelCoverageTest`** | `testProcessingOutcomeEnum` | Enum values check | All 3 enum values present and valid |

---

## 3. Test Coverage Matrix - Frontend (JavaScript)

| Test File | Test Case | Input | Expected Outcome |
| :--- | :--- | :--- | :--- |
| **`dashboard.test.js`** | `shows awarded points as a successful payment` | `{ pointsAwarded: 1500, outcome: "AWARDED" }` | `title: "1,500 points credited"`, `tone: "success"` |
| **`dashboard.test.js`** | `shows a duplicate event as skipped rather than credited` | `{ pointsAwarded: 0, outcome: "DUPLICATE" }` | `title: "Duplicate event skipped"`, `tone: "neutral"` |
| **`dashboard.test.js`** | `shows when the member has reached the monthly cap` | `{ pointsAwarded: 0, outcome: "CAPPED" }` | `title: "Monthly cap reached"`, `tone: "warning"` |
| **`dashboard.test.js`** | `caps progress percent at 100 even if points exceed monthly cap` | `{ pointsThisMonth: 120000, monthlyCap: 100000 }` | `progressPercent: 100` |

---

## 4. Test Execution Results & Coverage Enhancement Metrics

### A. Coverage Metrics Summary (Baseline vs. Enhanced)

| Test Suite / Layer | Baseline Tests | Enhanced Tests | Increase (Delta) | Coverage Focus |
| :--- | :---: | :---: | :---: | :--- |
| **Backend: Deduplication Service (`ProcessedEventStore`)** | 0 unit tests | **5 unit tests** | **+5 (+100%)** | Atomic insertion, out-of-order retention, and 32-thread concurrency stress |
| **Backend: Business Logic (`PointsCalculator`)** | 0 unit tests | **6 unit tests** | **+6 (+100%)** | Unlinked/linked multipliers, streak bonus thresholds (<6, =6, >6 months), rounding |
| **Backend: Domain Models (`model.*`)** | 0 unit tests | **4 unit tests** | **+4 (+100%)** | Precondition null checks, immutability, thread-safe monthly accrual, enum completeness |
| **Backend: Engine Integration (`RewardsEngineTest`)** | 8 tests (15 runs) | **8 tests (15 runs)** | Preserved | Out-of-order & 16-worker concurrent redelivery (RepeatedTest x8) |
| **Backend Total** | **15 test runs** | **30 test runs** | **+15 (+100%)** | Complete end-to-end and unit layer isolation |
| **Frontend: View Model (`dashboard.test.js`)** | 3 tests | **4 tests** | **+1 (+33%)** | Edge cases: progress bar capping at 100% when balance exceeds monthly cap |
| **Grand Total** | **18 tests** | **34 test runs** | **+16 (+89%)** | **100% Passing (0 failures, 0 errors, 0 skipped)** |

---

### B. Detailed Breakdown of New Test Classes Added

1. **`ProcessedEventStoreTest.java` (New File):**
   - Directly tests the deduplication store independently of the full orchestration engine.
   - Tests out-of-order storage to ensure past event IDs are never discarded by newer events.
   - Validates the atomic `tryRecord()` contract: returns `true` on first arrival, `false` on duplicate arrival.
   - Includes a high-concurrency multi-threaded stress test with **32 parallel worker threads** synchronizing on a `CountDownLatch` to guarantee zero race conditions on simultaneous arrivals.

2. **`PointsCalculatorTest.java` (New File):**
   - Validates calculation logic in complete isolation from the engine.
   - Verifies base rates for linked ($2\times$) vs. unlinked ($1\times$) accounts.
   - Verifies boundary conditions for streak bonuses: below 6 months (0%), exactly 6 months (+10%), and above 6 months (+10%).
   - Asserts strict rounding down (`RoundingMode.DOWN`) for fractional bonuses.

3. **`ModelCoverageTest.java` (New File):**
   - Asserts constructor preconditions and `NullPointerException` safety for `PaymentEvent` and `PointsResult`.
   - Validates that `MemberAccount` properly and thread-safely merges points across multiple events in the same `YearMonth`.
   - Validates helper methods (`isSkippedAsDuplicate()`, `toString()`) and enum values in `ProcessingOutcome`.

4. **`web/dashboard.test.js` (Enhanced):**
   - Added boundary test verifying `progressPercent` is strictly capped at `100` even when a member's points exceed the monthly cap (`pointsThisMonth > monthlyCap`).


