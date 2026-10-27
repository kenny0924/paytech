import { flatten } from "../onboarding.ts";
import type { Application, FormSchema, RequirementError, RequirementItem } from "./model.ts";
import { activeAddressFormat, addressPath, availableTasks, draftPath, findField, isPersonRole, isVisible, taskBlocks, taskOf } from "./schema.ts";
import type { TaskKey } from "./schema.ts";

/**
 * 申请进度：需求清单 / 需求级错误 → 任务与字段锚点；任务状态；结果页阶段
 * <li>需求清单（CM-7）是"还缺什么"的唯一来源；读取失败时退回本地必填检查，只用于显示进度，不放行提交。</li>
 */
export type Anchor = { task: TaskKey; fieldKey?: string; personId?: string; requirementCode?: string; declarationType?: string };
export type TaskState = "done" | "attention" | "active" | "todo" | "locked";
export type Phase = "draft" | "returned" | "in_review" | "approved" | "rejected" | "expired" | "withdrawn";

const open = new Set(["CURRENTLY_DUE", "PAST_DUE"]);
/** "缺少"类代码只表示未填，任务显示进行中；其他代码（格式、过期、冲突、退回）显示需要处理。 */
const missingCodes = new Set(["FIELD_MISSING", "DOCUMENT_MISSING", "DECLARATION_MISSING", "UBO_MISSING", "SIGNATORY_MISSING",
  "DIRECTOR_MISSING", "RELATED_PERSON_INCOMPLETE"]);

/**
 * 主主体在给定草稿路径上的未满足、非"缺少"类问题（如编码不在目录或已停用）：路径 → 错误码
 * <li>这类问题本地校验发现不了（值来自下拉、后来被停用），任务页打开时据此把错误挂到字段上。</li>
 */
export function fieldRequirementIssues(app: Application, paths: Set<string>): Record<string, string> {
  if (app.requirementsStatus !== "AVAILABLE") return {};
  const issues: Record<string, string> = {};
  for (const item of app.requirements ?? []) {
    if (!open.has(item.status) || !item.errorCode || missingCodes.has(item.errorCode)) continue;
    if (item.subjectPartyId && item.subjectPartyId !== app.subjectPartyId) continue;
    if (paths.has(item.requirementKey)) issues[item.requirementKey] ??= item.errorCode;
  }
  return issues;
}

/** 把需求键或错误键定位到任务与具体字段 / 人员 / 材料槽 / 声明。 */
export function locate(key: string, subjectPartyId: string | null, kind: string | null, app: Application, schema: FormSchema): Anchor {
  const person = subjectPartyId && subjectPartyId !== app.subjectPartyId ? subjectPartyId : undefined;
  const upperKind = (kind ?? "").toUpperCase();
  if (upperKind === "DECLARATION" || key.startsWith("declaration."))
    return { task: "declarations", declarationType: key.split(".")[1] };
  if (upperKind === "DOCUMENT" || key.startsWith("document.")) {
    const requirementCode = key.split(".")[1];
    const slot = schema.materials.find((item) => item.requirementCode === requirementCode);
    return person || (slot && isPersonRole(slot.subjectRole))
      ? { task: "people", personId: person, requirementCode } : { task: "materials", requirementCode };
  }
  if (person || ["PERSON", "RELATION"].includes(upperKind) || key.startsWith("relation.") || key === "people")
    return { task: "people", personId: person, fieldKey: findPrefix(schema, key)?.fieldKey };
  const field = findPrefix(schema, key);
  if (!field) return { task: "profile" };
  for (const section of schema.sections) for (const group of section.groups)
    if (group.fields.includes(field)) return { task: taskOf(section, group, field), fieldKey: field.fieldKey };
  return { task: "profile", fieldKey: field.fieldKey };
}

/** 需求键可能比字段键更细（如 `address.postal_code`），取最长前缀匹配的字段。 */
function findPrefix(schema: FormSchema, key: string) {
  const exact = findField(schema, key);
  if (exact) return exact;
  let best: ReturnType<typeof findField>;
  for (const section of schema.sections) for (const group of section.groups) for (const field of group.fields)
    if ((key.startsWith(field.fieldKey + ".") || field.fieldKey.startsWith(key + "."))
      && (!best || field.fieldKey.length > best.fieldKey.length)) best = field;
  return best;
}

/** 主体草稿是否填写了某任务的任意字段。 */
function primaryFilled(app: Application, schema: FormSchema, task: TaskKey): { filled: boolean; missing: number } {
  const values = flatten(app.values);
  let filled = false, missing = 0;
  for (const block of taskBlocks(schema, task, "PRIMARY")) for (const field of block.fields) {
    if (!isVisible(field, values, schema)) continue;
    const paths = field.widget === "ADDRESS"
      ? (activeAddressFormat(schema, values)?.components ?? []).filter((c) => c.required).map(addressPath)
      : [draftPath(field)].filter((path): path is string => !!path);
    const hasValue = paths.some((path) => (values[path] ?? "").trim() || (values[path.replace(/\.identifierValue$/, ".identifierMasked")] ?? "").trim());
    if (hasValue) filled = true;
    else if (field.requirementLevel === "REQUIRED" && !field.requirementGroup) missing++;
  }
  return { filled, missing };
}

export type TaskSummary = { key: TaskKey; state: TaskState; open: number; total?: number; done?: number };

/**
 * 每个任务的状态
 * <li>有需求清单时：该任务无未满足项且已填写为已完成；有非"缺少"类代码或已逾期为需要处理。</li>
 * <li>退回时，CP-4 的需求级错误优先，未处理的错误让对应任务显示需要处理。</li>
 * <li>核对并提交在其他任务全部完成前不可开始。</li>
 */
export function taskSummaries(app: Application, schema: FormSchema, returned: RequirementError[] = []): TaskSummary[] {
  const tasks = availableTasks(schema);
  const items = app.requirementsStatus === "AVAILABLE" ? (app.requirements ?? []).filter((item) => open.has(item.status)) : null;
  const byTask = new Map<TaskKey, RequirementItem[]>();
  for (const item of items ?? []) {
    const anchor = locate(item.requirementKey, item.subjectPartyId, item.requirementKind, app, schema);
    byTask.set(anchor.task, [...(byTask.get(anchor.task) ?? []), item]);
  }
  const returnedTasks = new Set(returned.map((error) => locate(error.requirementKey, error.subjectPartyId, null, app, schema).task));
  const summaries = tasks.filter((task) => task !== "review").map((task): TaskSummary => {
    const pending = byTask.get(task) ?? [];
    const content = taskContent(app, schema, task);
    const openCount = items ? pending.length : content.missing;
    const attention = returnedTasks.has(task)
      || pending.some((item) => item.status === "PAST_DUE" || (item.errorCode && !missingCodes.has(item.errorCode)));
    const state: TaskState = attention ? "attention" : openCount === 0 && content.filled ? "done" : content.filled ? "active" : "todo";
    return { key: task, state, open: openCount, total: content.total, done: content.done };
  });
  const ready = summaries.every((summary) => summary.state === "done");
  return [...summaries, { key: "review", state: ready ? "todo" : "locked", open: 0 }];
}

function taskContent(app: Application, schema: FormSchema, task: TaskKey): { filled: boolean; missing: number; total?: number; done?: number } {
  if (task === "people") {
    const people = app.people.length;
    return { filled: people > 0, missing: people > 0 ? 0 : 1, total: people };
  }
  if (task === "materials") {
    const slots = schema.materials.filter((slot) => !isPersonRole(slot.subjectRole));
    const done = slots.filter((slot) => app.documents.some((doc) => doc.requirementCode === slot.requirementCode
      && doc.subjectPartyId === app.subjectPartyId)).length;
    const required = slots.filter((slot) => slot.required).length;
    const requiredDone = slots.filter((slot) => slot.required && app.documents.some((doc) =>
      doc.requirementCode === slot.requirementCode && doc.subjectPartyId === app.subjectPartyId)).length;
    return { filled: done > 0, missing: required - requiredDone, total: slots.length, done };
  }
  if (task === "declarations") {
    const done = schema.declarations.filter((item) => declared(app, item.declarationType)).length;
    return { filled: done > 0, missing: schema.declarations.length - done, total: schema.declarations.length, done };
  }
  return primaryFilled(app, schema, task);
}

/**
 * 当前生效的声明：同一类型取最新一条，值为 TRUE 才算已确认
 * <li>草稿读出没有声明记录时，退回用需求清单判断：没有未满足的 `declaration.<TYPE>` 即已确认。</li>
 */
export function declared(app: Application, type: string): boolean {
  const records = app.declarations.filter((item) => item.declarationType === type);
  if (records.length) return records[records.length - 1].declaredValue === "TRUE";
  if (app.requirementsStatus !== "AVAILABLE" || !["DRAFT", "RETURNED"].includes(app.status)) return false;
  return !(app.requirements ?? []).some((item) => item.requirementKey === "declaration." + type && open.has(item.status));
}

/** 结果页阶段：申请状态为主，撤回时由案件结果区分"逾期"与"主动撤回"。 */
export function phaseOf(app: Pick<Application, "status" | "review">): Phase {
  switch (app.status) {
    case "RETURNED": return "returned";
    case "SUBMITTED": case "UNDER_REVIEW": return "in_review";
    case "APPROVED": return "approved";
    case "REJECTED": return "rejected";
    case "WITHDRAWN": case "EXPIRED":
      return app.status === "EXPIRED" || app.review?.outcome === "EXPIRED" || app.review?.caseStatus === "EXPIRED" ? "expired" : "withdrawn";
    default: return "draft";
  }
}

/** 截止前剩余整天数（向上取整）；已过期为负数；无期限为 null。 */
export function daysLeft(due: string | null | undefined, now = Date.now()): number | null {
  if (!due) return null;
  const time = Date.parse(due);
  if (!Number.isFinite(time)) return null;
  return Math.ceil((time - now) / 86_400_000);
}
