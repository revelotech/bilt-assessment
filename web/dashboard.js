const toneClasses = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-800",
  neutral: "border-slate-200 bg-slate-50 text-slate-700",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
};

const numberFormatter = new Intl.NumberFormat("en-US");

export function buildViewModel(result, member) {
  const progressPercent = Math.min(
    100,
    (member.pointsThisMonth / member.monthlyCap) * 100,
  );

  switch (result.outcome) {
    case "AWARDED":
      return {
        title: `${numberFormatter.format(result.pointsAwarded)} points credited`,
        description: "Your rent payment was processed successfully.",
        tone: "success",
        progressPercent,
      };
    case "DUPLICATE":
      return {
        title: "Duplicate event skipped",
        description: "This payment event was already processed. No additional points were credited.",
        tone: "neutral",
        progressPercent,
      };
    case "CAPPED":
      return {
        title: "Monthly cap reached",
        description: "You have reached your monthly points cap. No points were credited for this payment.",
        tone: "warning",
        progressPercent,
      };
    default:
      throw new Error(`Unknown processing outcome: ${result.outcome}`);
  }
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
