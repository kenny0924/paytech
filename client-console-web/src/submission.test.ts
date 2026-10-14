import { test } from "node:test";
import assert from "node:assert/strict";
import { describeIssue, readCheck } from "./submission.ts";
import type { Application } from "./onboarding.ts";
const app = {
  applicationId: "10",
  subjectPartyId: "30",
  materialSubjects: [
    {
      subjectPartyId: "30",
      requirementsStatus: "AVAILABLE",
      requirements: [
        { requirementCode: "REGISTRATION", documentType: "REGISTRATION_CERTIFICATE", requiredFlag: true },
      ],
    },
  ],
  documents: [
    {
      documentId: "50",
      subjectPartyId: "31",
      fileId: "40",
      requirementCode: "IDENTITY",
      documentType: "IDENTITY_DOCUMENT",
      subjectInScope: false,
      fileStatus: "AVAILABLE",
      file: { displayName: "passport.pdf" },
    },
  ],
} as unknown as Application;
const ctx = {
  app,
  labels: { "individual.givenName": "名", "organization.legalName": "组织法定名称" },
  subjectName: (party: string) => (party === "30" ? "主体" : "张三"),
  materialName: (type: string) => (type === "REGISTRATION_CERTIFICATE" ? "组织注册证明" : type),
};
test("issues are described by section, person and material", () => {
  assert.deepEqual(describeIssue({ field: "organization.legalName", message: "提交前请填写此项" }, ctx), {
    where: "组织法定名称",
    message: "提交前请填写此项",
  });
  assert.equal(describeIssue({ field: "people[31].individual.givenName", message: "x" }, ctx).where, "张三 · 名");
  assert.equal(describeIssue({ field: "materials[30].REGISTRATION", message: "x" }, ctx).where, "主体 · 组织注册证明");
  assert.equal(describeIssue({ field: "documents[50]", message: "x" }, ctx).where, "passport.pdf");
  assert.equal(describeIssue({ field: "people", message: "x" }, ctx).where, "相关人员");
  assert.equal(describeIssue({ field: "unknown.path", message: "x" }, ctx).where, "unknown.path");
});
test("check results must be well-formed and consistent", () => {
  assert.deepEqual(readCheck({ ready: true, issues: [] }), { ready: true, issues: [] });
  assert.throws(() => readCheck({ ready: true, issues: [{ field: "a", message: "b" }] }));
  assert.throws(() => readCheck({ ready: false, issues: [] }));
  assert.throws(() => readCheck({ ready: "yes", issues: [] }));
  assert.throws(() => readCheck(null));
});
