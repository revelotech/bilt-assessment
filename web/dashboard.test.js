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
  assert.equal(view.description, "Your rent payment was processed successfully.");
  assert.equal(view.progressPercent, 1.5);
});

test("shows a duplicate event as skipped rather than credited", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "DUPLICATE" },
    member,
  );

  assert.equal(view.title, "Duplicate event skipped");
  assert.equal(view.tone, "neutral");
  assert.equal(
    view.description,
    "This payment event was already processed. No additional points were credited.",
  );
});

test("shows when the member has reached the monthly cap", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "CAPPED" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "Monthly cap reached");
  assert.equal(view.tone, "warning");
  assert.equal(
    view.description,
    "You have reached your monthly points cap. No additional points were credited.",
  );
  assert.equal(view.progressPercent, 100);
});

test("keeps duplicate status neutral even when the monthly cap is full", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "DUPLICATE" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "Duplicate event skipped");
  assert.equal(view.tone, "neutral");
  assert.equal(view.progressPercent, 100);
});

test("rejects an unsupported outcome instead of claiming success", () => {
  assert.throws(
    () => buildViewModel({ pointsAwarded: 0, outcome: "UNKNOWN" }, member),
    /Unsupported processing outcome/,
  );
});
