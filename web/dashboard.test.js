import test from "node:test";
import assert from "node:assert/strict";

import { buildViewModel, renderDashboard } from "./dashboard.js";

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
  assert.equal(view.description,
    "This payment event was already processed. No additional points were credited.");
  assert.equal(view.progressPercent, 1.5);
});

test("shows when the member has reached the monthly cap", () => {
  const view = buildViewModel(
    { pointsAwarded: 0, outcome: "CAPPED" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "Monthly cap reached");
  assert.equal(view.tone, "warning");
  assert.equal(view.description,
    "You have reached your monthly points cap. No points were credited for this payment.");
  assert.equal(view.progressPercent, 100);
});

test("uses the outcome even when an award reaches the cap", () => {
  const view = buildViewModel(
    { pointsAwarded: 500, outcome: "AWARDED" },
    { ...member, pointsThisMonth: 100_000 },
  );

  assert.equal(view.title, "500 points credited");
  assert.equal(view.tone, "success");
  assert.equal(view.progressPercent, 100);
});

test("rejects unknown outcomes instead of announcing success", () => {
  assert.throws(
    () => buildViewModel({ pointsAwarded: 0, outcome: "UNKNOWN" }, member),
    /Unknown processing outcome: UNKNOWN/,
  );
});

test("updates visible copy and replaces the status color for each outcome", (t) => {
  const elements = Object.fromEntries([
    "status", "status-title", "status-description", "points", "streak",
    "progress", "progress-label",
  ].map((name) => [`[data-${name}]`, { className: "", textContent: "", style: {} }]));
  const originalDocument = globalThis.document;
  globalThis.document = { querySelector: (selector) => elements[selector] };
  t.after(() => {
    if (originalDocument === undefined) {
      delete globalThis.document;
    } else {
      globalThis.document = originalDocument;
    }
  });

  for (const [outcome, color] of [
    ["AWARDED", "emerald"], ["DUPLICATE", "slate"], ["CAPPED", "amber"],
  ]) {
    const result = { outcome, pointsAwarded: outcome === "AWARDED" ? 1500 : 0 };
    const currentMember = {
      ...member,
      pointsThisMonth: outcome === "CAPPED" ? 100_000 : member.pointsThisMonth,
    };
    const view = buildViewModel(result, currentMember);

    renderDashboard(result, currentMember);

    assert.equal(elements["[data-status]"].className,
      `rounded-2xl border p-5 border-${color}-200 bg-${color}-50 text-${color}-${color === "slate" ? 700 : 800}`);
    assert.equal(elements["[data-status-title]"].textContent, view.title);
    assert.equal(elements["[data-status-description]"].textContent, view.description);
    assert.equal(elements["[data-points]"].textContent,
      outcome === "CAPPED" ? "100,000" : "1,500");
    assert.equal(elements["[data-streak]"].textContent, "6 month streak");
    assert.equal(elements["[data-progress]"].style.width, `${view.progressPercent}%`);
    assert.equal(elements["[data-progress-label]"].textContent,
      `${Math.round(view.progressPercent)}% of monthly cap`);
  }
});
