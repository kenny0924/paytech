import type { Status } from "../content/requirements";

const labels: Record<Status, string> = {
  confirmed: "Confirmed",
  proposed: "Proposed",
  mvp: "MVP",
  later: "Later",
  open: "Open Question",
};

export function StatusBadge({ status }: { status: Status }) {
  return <span className={`status-badge status-${status}`}>{labels[status]}</span>;
}
