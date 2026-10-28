import { RequestFailure } from "../auth.ts";
import { patch } from "../onboarding.ts";
import type { Flat } from "../onboarding.ts";
import { translator } from "../i18n/index.ts";
import type { FormSchema, RequirementError, SchemaField } from "./model.ts";
import { problemErrors } from "./model.ts";
import { isDocumentOutdated } from "./declarations.ts";
import { SCHEMA_NAMESPACE, activeAddressFormat, addressPath, draftPath, fieldIssue, findField, identifierPaths, isIdentifierLead, isLocalKey, isVisible, selectedIdentifier } from "./schema.ts";
import type { FieldBlock, Issue } from "./schema.ts";

const to = translator("onboarding");

const ts = translator(SCHEMA_NAMESPACE);
/**
 * 需求级错误码 → `onboarding-schema.error.<CODE>`、补救码 → `onboarding-schema.remediation.<CODE>`（文案随国家包维护）
 * <li>未登记的代码显示通用说明，不展示服务端原文。</li>
 */
export function errorText(code: string | null | undefined): string {
  const text = code ? ts("error." + code) : "";
  return text && text !== SCHEMA_NAMESPACE + ".error." + code ? text : to("error.generic");
}
export function remediationText(code: string | null | undefined): string | null {
  if (!code || code === "NONE") return null;
  const text = ts("remediation." + code);
  return text === SCHEMA_NAMESPACE + ".remediation." + code ? null : text;
}
export const issueText = (issue: Issue) => to(issue.key, issue.params);

/** 某字段参与校验与提交的草稿路径（地址按组件展开，证件含类型、签发国与到期日）。 */
export function fieldPaths(field: SchemaField, schema: FormSchema, values: Flat = {}): { path: string; required: boolean; field: SchemaField }[] {
  if (field.widget === "ADDRESS")
    return (activeAddressFormat(schema, values)?.components ?? []).map((component) => ({ path: addressPath(component), required: component.required, field }));
  if (field.widget === "IDENTIFIER") {
    const type = selectedIdentifier(field, schema, values), required = field.requirementLevel === "REQUIRED";
    return [
      { path: identifierPaths.value, required, field },
      ...(type?.issuerScopeMode === "COUNTRY" ? [{ path: identifierPaths.issuer, required, field }] : []),
      ...(type?.expiryRequired ? [{ path: identifierPaths.expiryDate, required, field }] : []),
    ];
  }
  const path = draftPath(field);
  return path ? [{ path, required: field.requirementLevel === "REQUIRED" && !field.requirementGroup, field }] : [];
}

/**
 * 本地检查一组字段：格式问题总是报告；缺失只在 `requireFilled`（继续 / 提交前）时报告
 * <li>证件已有掩码（服务端已保存）视为已填写。</li>
 * <li>二选一组（requirementGroup）中任一项填写即满足，未满足时挂在组内第一项。</li>
 */
export function localIssues(blocks: FieldBlock[], values: Flat, schema: FormSchema, requireFilled: boolean): Record<string, Issue> {
  const issues: Record<string, Issue> = {};
  const groups = new Map<string, { first: string; filled: boolean }>();
  for (const block of blocks) for (const field of block.fields) {
    if (!isVisible(field, values, schema) || !isIdentifierLead(field, schema)) continue;
    for (const { path, required } of fieldPaths(field, schema, values)) {
      const value = values[path] ?? "";
      const masked = path === identifierPaths.value && (values[identifierPaths.masked] ?? "");
      if (!value.trim() && masked) continue;
      const type = path === identifierPaths.value ? selectedIdentifier(field, schema, values) : undefined;
      const formatOnly = { ...field, requirementLevel: "OPTIONAL" as const };
      const issue = path === identifierPaths.value || path === draftPath(field)
        ? fieldIssue(formatOnly, value, false, type?.pattern, type?.checksumRule) : null;
      if (issue) issues[path] = issue;
      else if (requireFilled && required && !value.trim()) issues[path] = { key: "error.field_missing" };
      if (field.requirementGroup && field.widget !== "IDENTIFIER" && path === draftPath(field)) {
        const entry = groups.get(field.requirementGroup) ?? { first: path, filled: false };
        entry.filled ||= !!value.trim() || !!masked;
        groups.set(field.requirementGroup, entry);
      }
    }
  }
  if (requireFilled) for (const entry of groups.values()) if (!entry.filled) issues[entry.first] ??= { key: "error.group_missing" };
  return issues;
}

/** 草稿 PATCH 体：只含改动且本地格式通过的字段；界面状态键与掩码不提交；证件值规范化前原样提交（服务端规范化）。 */
export function draftPatch(baseline: Flat, values: Flat, blocked: Set<string>) {
  const keys = Object.keys(values).filter((key) => !isLocalKey(key) && !blocked.has(key));
  const before = Object.fromEntries(keys.map((key) => [key, baseline[key] ?? ""]));
  const after = Object.fromEntries(keys.map((key) => [key, values[key] ?? ""]));
  return patch(before, after);
}

/** 服务端保存 / 提交失败中的字段级错误 → 草稿路径；旧格式（field + message）与需求级格式（requirementKey + code）都接受。 */
export function serverIssues(error: unknown, schema: FormSchema): Record<string, string> {
  if (!(error instanceof RequestFailure)) return {};
  const result: Record<string, string> = {};
  for (const item of problemErrors(error.problem)) {
    const path = requirementPath(item, schema);
    if (path) result[path] = errorText(item.code);
  }
  for (const [field] of Object.entries(error.fields)) if (/^[\w:]+\.\w+$/.test(field) && !result[field]) result[field] = to("error.generic");
  return result;
}

/** 需求键 → 草稿路径；找不到对应字段时为空（由页面级汇总承担）。 */
export function requirementPath(item: Pick<RequirementError, "requirementKey">, schema: FormSchema): string | null {
  const field = findField(schema, item.requirementKey);
  if (field) return field.widget === "ADDRESS" ? fieldPaths(field, schema)[0]?.path ?? null : draftPath(field);
  const address = [...(schema.addressFormat?.components ?? []), ...(schema.fallbackAddressFormat?.components ?? [])].find((component) => item.requirementKey.toLowerCase().endsWith("." + component.component.toLowerCase()));
  return address ? addressPath(address) : null;
}

/** 入驻接口会返回、页面需要单独说明的 Problem 代码；其余按状态码给通用说明。 */
export const requestErrorCodes = ["INVALID_DRAFT", "APPLICATION_NOT_EDITABLE", "APPLICATION_NOT_WITHDRAWABLE", "APPLICATION_NOT_FOUND",
  "ONBOARDING_CODE_INVALID", "JURISDICTION_NOT_ACCEPTED", "SUBMISSION_INCOMPLETE", "IDENTITY_CONFLICT", "IDENTIFIER_CONFLICT",
  "GATEWAY_CONCURRENCY_LIMITED", "DECLARATION_DOCUMENT_OUTDATED"] as const;
/**
 * 请求失败的页面文案：只用代码与状态码映射本地化文案，不显示服务端 detail（可能是另一种语言或内部说明）
 * <li>网络失败（状态 0）沿用认证客户端已本地化的说明。</li>
 */
export function requestErrorText(error: unknown): string {
  if (!(error instanceof RequestFailure)) return error instanceof Error ? error.message : to("page.action_failed");
  if (error.status === 0) return error.message;
  // INVALID_DRAFT 没有指明字段时不能说"检查标出的字段"。
  if (error.code === "INVALID_DRAFT" && !Object.keys(error.fields).length && !problemErrors(error.problem).length)
    return to("request_error.invalid_draft_unspecified");
  if ((requestErrorCodes as readonly string[]).includes(error.code)) return to("request_error." + error.code.toLowerCase());
  // 条款版本过期以字段错误（termsVersion）下发，Problem 代码可能是通用的参数错误。
  if (isDocumentOutdated(error)) return to("request_error.declaration_document_outdated");
  if (error.status === 429) return to("request_error.gateway_concurrency_limited");
  if (error.status >= 500) return to("request_error.unavailable");
  return to("page.action_failed");
}

/**
 * 串行队列：每个任务在前一个结束（成功或失败）后才开始；前一个的失败不传给后一个，各自的结果只交给自己的调用方
 * <li>草稿自动保存用它避免同一申请并发 PATCH（Gateway 并发限制会返回 429）。</li>
 */
export function serialQueue() {
  let tail: Promise<unknown> = Promise.resolve();
  return <T>(task: () => Promise<T>): Promise<T> => {
    const next = tail.catch(() => undefined).then(task);
    tail = next;
    return next;
  };
}

/**
 * 被 Gateway 限流（429）时按退避重试同一请求；其他失败立即抛出
 * <li>重试的是同一 PATCH 体，不丢改动；退避用尽仍失败则交给调用方显示"未保存"。</li>
 */
export async function withBackoff<T>(task: () => Promise<T>, delays: number[] = [800, 1600, 3200],
  wait: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms))): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    try { return await task(); }
    catch (failure) {
      if (!(failure instanceof RequestFailure) || failure.status !== 429 || attempt >= delays.length) throw failure;
      await wait(delays[attempt]);
    }
  }
}

/**
 * 发送一批字段；422 时隔离被拒字段，其余字段照常保存
 * <li>服务端指明字段：这些字段被拒，其余立即重发一次。</li>
 * <li>未指明字段：逐个重发，仍 422 的字段被拒。</li>
 * <li>非 422 失败直接抛出（由调用方显示"未保存"并在下次保存重发）。</li>
 *
 * @return 被拒字段 → 显示文案；空表示全部保存
 */
export async function sendIsolating(fields: string[], send: (fields: string[]) => Promise<void>,
  fieldErrors: (failure: unknown) => Record<string, string>, fallback: string): Promise<Record<string, string>> {
  const refused: Record<string, string> = {};
  const unprocessable = (failure: unknown) => failure instanceof RequestFailure && failure.status === 422;
  try { await send(fields); return refused; }
  catch (failure) {
    if (!unprocessable(failure)) throw failure;
    const mapped = fieldErrors(failure);
    const named = fields.filter((key) => mapped[key]);
    if (named.length) {
      for (const key of named) refused[key] = mapped[key];
      const rest = fields.filter((key) => !mapped[key]);
      if (rest.length) Object.assign(refused, await sendIsolating(rest, send, fieldErrors, fallback));
      return refused;
    }
    if (fields.length === 1) { refused[fields[0]] = fallback; return refused; }
    for (const key of fields) Object.assign(refused, await sendIsolating([key], send, fieldErrors, fallback));
    return refused;
  }
}
