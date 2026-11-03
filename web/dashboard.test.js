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
    { pointsAwarded: 1_500, outcome: "AWARDED" },
    member,
  );

  assert.equal(view.title, "1,500 points credited");
  assert.equal(view.tone, "success");
  assert.equal(view.progressPercent, 1.5);
});

test("shows a duplicate event as skipped rather than credited", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "DUPLICATE" },
    member,
  );

  assert.equal(view.title, "Duplicate event skipped");
  assert.equal(view.tone, "neutral");
});

test("shows when the member has reached the monthly cap", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "CAPPED" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "Monthly cap reached");
  assert.equal(view.tone, "warning");
  assert.equal(view.progressPercent, 100);
});

test("never claims points were credited for a duplicate", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "DUPLICATE" },
    member,
  );

  assert.doesNotMatch(view.title, /credited/);
  assert.match(view.description, /already processed/);
  assert.equal(view.progressPercent, 1.5);
});

test("never claims points were credited when the cap blocked the award", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "CAPPED" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.doesNotMatch(view.title, /credited/);
  assert.match(view.description, /cap/);
});

test("trusts the outcome rather than guessing from pointsAwarded", () => {
  // A payment that only partially fit under the cap is still AWARDED.
  const partial = buildViewModel(
    { pointsAwarded: 250, outcome: "AWARDED" },
    { ...member, pointsThisMonth: 100_000 },
  );
  assert.equal(partial.title, "250 points credited");
  assert.equal(partial.tone, "success");
  assert.equal(partial.progressPercent, 100);

  // Zero points with a DUPLICATE outcome is neutral, not a cap warning.
  const duplicate = buildViewModel(
    { pointsAwarded: 0, outcome: "DUPLICATE" },
    { ...member, pointsThisMonth: 100_000 },
  );
  assert.equal(duplicate.tone, "neutral");
});

test("each outcome uses a distinct visual tone", () => {
  const tones = ["AWARDED", "DUPLICATE", "CAPPED"].map(
    (outcome) => buildViewModel({ pointsAwarded: 0, outcome }, member).tone,
  );

  assert.deepEqual(tones, ["success", "neutral", "warning"]);
});

test("rejects an unknown outcome instead of defaulting to success", () => {
  assert.throws(
    () => buildViewModel({ pointsAwarded: 10, outcome: "SOMETHING_ELSE" }, member),
    /Unknown processing outcome/,
  );
});
