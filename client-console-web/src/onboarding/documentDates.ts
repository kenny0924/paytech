import type { MaterialSlot } from "./model.ts";
import type { Issue } from "./schema.ts";

/**
 * 材料签发日期与到期日的规则（随关联提交）
 * <li>槽位有时效（maxAgeDays）时签发日期必填并按时效检查；槽位标明需到期日（expiryRequired）时到期日必填；其余均可选。</li>
 * <li>签发日期不能晚于今天；到期日必须晚于今天（因此必然晚于签发日期）。服务端仍按程序规则复核。</li>
 */
export type DateRules = { issueRequired: boolean; expiryRequired: boolean };
export type DateIssues = { issueDate?: Issue; expiryDate?: Issue };

export function dateRules(slot: Pick<MaterialSlot, "maxAgeDays" | "expiryRequired">): DateRules {
  return { issueRequired: !!slot.maxAgeDays && slot.maxAgeDays > 0, expiryRequired: slot.expiryRequired === true };
}

/** 本地日期（YYYY-MM-DD），与日期控件同一时区。 */
export function today(now = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate());
}

const valid = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [y, m, d] = value.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
};
const days = (from: string, to: string) => Math.round((Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z")) / 86_400_000);

export function documentDateIssues(slot: Pick<MaterialSlot, "maxAgeDays" | "expiryRequired">, issueDate: string, expiryDate: string,
  on: string = today()): DateIssues {
  const rules = dateRules(slot), result: DateIssues = {};
  const issue = issueDate.trim(), expiry = expiryDate.trim();
  if (!issue) { if (rules.issueRequired) result.issueDate = { key: "error.field_missing" }; }
  else if (!valid(issue)) result.issueDate = { key: "error.format" };
  else if (issue > on) result.issueDate = { key: "error.issue_future" };
  else if (slot.maxAgeDays && days(issue, on) > slot.maxAgeDays)
    result.issueDate = { key: "error.document_too_old", params: { months: Math.max(1, Math.round(slot.maxAgeDays / 30)) } };
  if (!expiry) { if (rules.expiryRequired) result.expiryDate = { key: "error.field_missing" }; }
  else if (!valid(expiry)) result.expiryDate = { key: "error.format" };
  else if (expiry <= on) result.expiryDate = { key: "error.expired" };
  return result;
}
