const toneClasses = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-800",
  neutral: "border-slate-200 bg-slate-50 text-slate-700",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
};

const numberFormatter = new Intl.NumberFormat("en-US");

function buildStatusView(result) {
  switch (result.outcome) {
    case "AWARDED":
      return {
        title: `${numberFormatter.format(result.pointsAwarded)} points credited`,
        description: "Your rent payment earned points within your monthly limit.",
        tone: "success",
      };
    case "DUPLICATE":
      return {
        title: "Duplicate event skipped",
        description: "This payment event was already received. No additional points were credited.",
        tone: "neutral",
      };
    case "CAPPED":
      return {
        title: "Monthly cap reached",
        description: "You have reached your monthly points limit. No points were credited for this payment.",
        tone: "warning",
      };
    default:
      throw new Error(`Unknown processing outcome: ${result.outcome}`);
  }
}

export function buildViewModel(result, member) {
  const progressPercent = Math.min(
    100,
    (member.pointsThisMonth / member.monthlyCap) * 100,
  );

  return {
    ...buildStatusView(result),
    progressPercent,
  };
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
