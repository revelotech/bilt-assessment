const toneClasses = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-800",
  neutral: "border-slate-200 bg-slate-50 text-slate-700",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
};

const numberFormatter = new Intl.NumberFormat("en-US");

// One entry per ProcessingOutcome on the backend. The UI reads the outcome
// reported by the server instead of inferring it from pointsAwarded.
const outcomeViews = {
  AWARDED: (result) => ({
    title: `${numberFormatter.format(result.pointsAwarded)} points credited`,
    description: "Your rent payment was processed successfully.",
    tone: "success",
  }),
  DUPLICATE: () => ({
    title: "Duplicate event skipped",
    description:
      "This payment was already processed, so no additional points were credited.",
    tone: "neutral",
  }),
  CAPPED: () => ({
    title: "Monthly cap reached",
    description:
      "Your payment was received, but you've hit this month's points cap, so no points were credited.",
    tone: "warning",
  }),
};

export function buildViewModel(result, member) {
  const buildOutcomeView = outcomeViews[result.outcome];
  if (!buildOutcomeView) {
    throw new Error(`Unknown processing outcome: ${result.outcome}`);
  }

  const progressPercent = Math.min(
    100,
    (member.pointsThisMonth / member.monthlyCap) * 100,
  );

  return { ...buildOutcomeView(result), progressPercent };
}

export function renderDashboard(result, member) {
  const view = buildViewModel(result, member);

  document.querySelector("[data-status]").className =
    `rounded-2xl border p-5 ${toneClasses[view.tone]}`;
  document.querySelector("[data-status-title]").textContent = view.title;
  document.querySelector("[data-status-description]").textContent =
    view.description;
  document.querySelector("[data-points]").textContent = numberFormatter.format(
    member.pointsThisMonth,
  );
  document.querySelector("[data-streak]").textContent =
    `${member.streakMonths} month streak`;
  document.querySelector("[data-progress]").style.width =
    `${view.progressPercent}%`;
  document.querySelector("[data-progress-label]").textContent =
    `${Math.round(view.progressPercent)}% of monthly cap`;
}
