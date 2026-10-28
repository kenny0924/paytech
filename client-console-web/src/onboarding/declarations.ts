import { RequestFailure } from "../auth.ts";
import type { DeclarationDef, ThresholdComparison } from "./model.ts";
import { problemErrors } from "./model.ts";

/**
 * 声明写入与条款文本
 * <li>带文档的声明（documentVersion 非空）确认时带上所读版本 termsVersion；服务端版本已更新时返回 422 DECLARATION_DOCUMENT_OUTDATED。</li>
 * <li>条款正文随 Client 语言包发布：`onboarding.legal.<声明类型>.<版本>.title|body`，段名小写、非字母数字转为下划线。</li>
 */
export const DECLARATION_DOCUMENT_OUTDATED = "DECLARATION_DOCUMENT_OUTDATED";

/** 声明写入体；只有带文档的声明才发送 termsVersion。 */
export function declarationBody(item: Pick<DeclarationDef, "declarationType" | "documentVersion">, value: boolean) {
  return { declarationType: item.declarationType, declaredValue: value ? "TRUE" : "FALSE",
    ...(item.documentVersion ? { termsVersion: item.documentVersion } : {}) };
}

const segment = (value: string) => value.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");

/** 条款文本键（onboarding 命名空间内，不含命名空间前缀）。 */
export function legalTextKey(declarationType: string, version: string, part: "title" | "body"): string {
  return "legal." + segment(declarationType) + "." + segment(version) + "." + part;
}

/**
 * 声明所附文档的正文是否可读：无文档的声明恒为可读；有文档时该版本的标题与正文都要随语言包发布
 * <li>不可读时页面不允许确认，避免接受未展示过的文本。</li>
 *
 * @param translate onboarding 命名空间的翻译函数；键缺失时返回完整键名
 */
export function documentReadable(item: Pick<DeclarationDef, "declarationType" | "documentVersion">, translate: (key: string) => string): boolean {
  if (!item.documentVersion) return true;
  return (["title", "body"] as const).every((part) => {
    const key = legalTextKey(item.declarationType, item.documentVersion!, part);
    return translate(key) !== "onboarding." + key;
  });
}

/** 正文按空行分段。 */
export function paragraphs(body: string): string[] {
  return body.split(/\n\s*\n/).map((item) => item.trim()).filter(Boolean);
}

/** 写入声明被拒是否因为条款版本已更新（Problem 代码、字段错误或需求级错误任一指明）。 */
export function isDocumentOutdated(error: unknown): boolean {
  if (!(error instanceof RequestFailure)) return false;
  if (error.code === DECLARATION_DOCUMENT_OUTDATED) return true;
  if (Object.values(error.fields).some((message) => message.split("/")[0] === DECLARATION_DOCUMENT_OUTDATED)) return true;
  return problemErrors(error.problem).some((item) => item.code === DECLARATION_DOCUMENT_OUTDATED);
}

/** 门槛文案键：GTE 用"{n}% 或以上"的基础键，GT 用 `_gt` 后缀的"超过 {n}%"。 */
export function thresholdKey(base: string, comparison: ThresholdComparison): string {
  return comparison === "GT" ? base + "_gt" : base;
}

/** 使用门槛文案的键（覆盖测试据此核对两套文案都存在）。 */
export const thresholdKeys = ["people.intro", "people.check_ubo", "start.checklist_people"] as const;
