import type { Application } from "./onboarding.ts";
import { translator } from "./i18n/index.ts";

const to = translator("onboarding");
export type SubmissionIssue = { field: string; message: string };
export type SubmissionCheck = { ready: boolean; issues: SubmissionIssue[] };
export type IssueContext = {
  app: Application;
  labels: Record<string, string>;
  subjectName: (partyId: string) => string;
  materialName: (documentType: string) => string;
};
// 校验 CCT 核对结果的结构；未知形状不能被当作“可以提交”。
export function readCheck(value: unknown): SubmissionCheck {
  const check = value as SubmissionCheck | null;
  if (
    !check ||
    typeof check.ready !== "boolean" ||
    !Array.isArray(check.issues) ||
    check.issues.some(
      (issue) =>
        typeof issue?.field !== "string" || typeof issue?.message !== "string",
    ) ||
    check.ready !== (check.issues.length === 0)
  )
    throw new Error(to("submit.check_unavailable"));
  return check;
}
// 把 CCT 的问题定位翻译成页面上能看懂的位置；未知定位原样显示，不丢弃。
export function describeIssue(
  issue: SubmissionIssue,
  ctx: IssueContext,
): { where: string; message: string } {
  const person = /^people\[(\d+)\]\.(.+)$/.exec(issue.field);
  if (person)
    return {
      where: ctx.subjectName(person[1]) + " · " + (ctx.labels[person[2]] ?? person[2]),
      message: issue.message,
    };
  const material = /^materials\[(\d+)\]\.(.+)$/.exec(issue.field);
  if (material) {
    const requirement = ctx.app.materialSubjects
      .find((s) => s.subjectPartyId === material[1])
      ?.requirements.find((r) => r.requirementCode === material[2]);
    return {
      where:
        ctx.subjectName(material[1]) +
        " · " +
        (requirement ? ctx.materialName(requirement.documentType) : material[2]),
      message: issue.message,
    };
  }
  const document = /^documents\[(\d+)\]$/.exec(issue.field);
  if (document) {
    const doc = ctx.app.documents.find((d) => d.documentId === document[1]);
    return {
      where: doc
        ? (doc.file?.displayName ?? ctx.materialName(doc.documentType))
        : to("issue.material"),
      message: issue.message,
    };
  }
  if (issue.field === "people") return { where: to("section.people"), message: issue.message };
  return { where: ctx.labels[issue.field] ?? issue.field, message: issue.message };
}
