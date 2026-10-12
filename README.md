# Bilt Full-Stack Engineering Assessment

## Context

RentRewards is a rewards platform that lets members earn points on rent and
mortgage payments. Points can later be redeemed for travel, cash back, and
other perks — similar in spirit to how a credit card rewards program works,
but built specifically around housing payments.

Members' payments are processed by an external payment processor. Whenever a
payment goes through, the processor sends us a **webhook event**, and our
`RewardsEngine` calculates and awards points based on a few business rules.

## Business rules (already implemented)

- **Base rate:** 1 point per $1 paid.
- **Linked account bonus:** payments made from a bank account linked to
  RentRewards earn **2x** points.
- **Streak bonus:** members with 6+ consecutive months of on-time payments
  get an extra **10%** on top of their (already multiplied) points.
- **Monthly cap:** members can earn at most **100,000 points per calendar
  month**.

## The incident

**Our payment processor guarantees at-least-once delivery** of webhook
events. In practice, this means the same payment event can be delivered to
us more than once — usually due to network retries on their side, timeouts,
or their own infrastructure hiccups. When that happens, we must **not**
award points twice for the same payment.

We've had two related reports:

1. Some members occasionally receive points twice after webhook retries.
   Deliveries may arrive out of order or at the same time on different
   workers.
2. The rewards dashboard always announces that points were credited, even
   when an event was skipped as a duplicate or the monthly cap prevented an
   award.

## Your task

1. Run both existing test suites (see `SETUP.md`).
2. Diagnose and fix the idempotency bug. The same `eventId` must award
   points at most once, including when deliveries arrive out of order or
   overlap on different workers.
3. Drive the dashboard from `PointsResult.getOutcome()` in `web/dashboard.js`:
   - `AWARDED`: success;
   - `DUPLICATE`: neutral/skipped;
   - `CAPPED`: warning.
4. Keep all existing business rules and passing expectations intact.
5. Add or improve tests when they make your reasoning clearer.

You may use any AI coding tool you normally work with. You do not need to
submit prompts, transcripts, or an account of your AI usage.

## What we're looking for

We evaluate the observable engineering result:

- Correctness under out-of-order and concurrent duplicate deliveries.
- Correct UI state, copy, and visual tone for each outcome.
- Focused, readable changes with useful tests.
- Preservation of the existing business rules.

This exercise uses in-memory state by design. A production database or
distributed idempotency solution is outside the expected scope.

## Time box

Aim for **45 minutes** and stop after **60 minutes**. A focused partial
solution is preferable to a large rewrite.

## Submission

Fork this public repository, commit your changes, and share the URL of your
public fork. Please do not squash away the baseline history: evaluation is
limited to your diff from the original repository.

## Solution notes

- **Idempotency:** `ProcessedEventStore` now keeps every processed `eventId`
  (it previously remembered only the last one, so `A → B → A` re-awarded A).
  `RewardsEngine.processPayment` runs the whole check → calculate → cap →
  award → mark-processed flow while holding the shared store's monitor. This
  makes duplicate detection and the monthly-cap read/update atomic, including
  across separate engine instances that share a store.
- **Dashboard:** `buildViewModel` maps each `outcome` to its own copy and tone
  (`AWARDED` → success, `DUPLICATE` → neutral, `CAPPED` → warning) and rejects
  unknown outcomes rather than claiming success.
- **Trade-offs:** processing is serialized per store, which keeps the
  in-memory exercise simple. Cross-process coordination, durability across
  restarts and pruning of old event IDs are out of scope.
- **Known limitations (left as-is to keep the existing rules intact):**
  - The engine reports `CAPPED` for any new event that earns zero points,
    including sub-$1 payments that round down to 0, so the dashboard can show
    "Monthly cap reached" before the cap is actually hit.
  - Event IDs are not validated: a `null` `eventId` is stored like any other
    ID, so later null-ID events would be treated as duplicates. Rejecting
    malformed webhooks belongs at the input boundary.
  - The monthly cap is only atomic for engines that share the same
    `ProcessedEventStore`; engines with separate stores updating the same
    `MemberAccount` are not coordinated.

Validate with `mvn test` and `npm run test:web`.
