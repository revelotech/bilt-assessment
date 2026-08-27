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
2. Diagnose and fix the idempotency bug. Claiming an event must be atomic:
   when concurrent workers receive the same `eventId`, at most one may
   award points.
3. Fix the dashboard states in `web/dashboard.js`:
   - awarded points: success;
   - duplicate event: neutral/skipped;
   - monthly cap reached: warning.
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
