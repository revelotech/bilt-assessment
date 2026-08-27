import test from "node:test";
import assert from "node:assert/strict";

import { buildViewModel } from "./dashboard.js";

const member = {
  pointsThisMonth: 1_500,
  monthlyCap: 100_000,
  streakMonths: 6,
};

test("shows awarded points as a successful payment", () => {
  const view = buildViewModel(
    { pointsAwarded: 1_500, skippedAsDuplicate: false },
    member,
  );

  assert.equal(view.title, "1,500 points credited");
  assert.equal(view.tone, "success");
  assert.equal(view.progressPercent, 1.5);
});

test("shows a duplicate event as skipped rather than credited", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, skippedAsDuplicate: true },
    member,
  );

  assert.equal(view.title, "Duplicate event skipped");
  assert.equal(view.tone, "neutral");
});

test("shows when the member has reached the monthly cap", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, skippedAsDuplicate: false },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "Monthly cap reached");
  assert.equal(view.tone, "warning");
  assert.equal(view.progressPercent, 100);
});
