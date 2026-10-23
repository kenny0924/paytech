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
  labels: { "individual.givenName": "Given name", "organization.legalName": "Organization legal name" },
  subjectName: (party: string) => (party === "30" ? "Applicant" : "张三"),
  materialName: (type: string) => (type === "REGISTRATION_CERTIFICATE" ? "Certificate of registration" : type),
};
test("issues are described by section, person and material", () => {
  assert.deepEqual(describeIssue({ field: "organization.legalName", message: "提交前请填写此项" }, ctx), {
    where: "Organization legal name",
    message: "提交前请填写此项",
  });
  assert.equal(describeIssue({ field: "people[31].individual.givenName", message: "x" }, ctx).where, "张三 · Given name");
  assert.equal(describeIssue({ field: "materials[30].REGISTRATION", message: "x" }, ctx).where, "Applicant · Certificate of registration");
  assert.equal(describeIssue({ field: "documents[50]", message: "x" }, ctx).where, "passport.pdf");
  assert.equal(describeIssue({ field: "people", message: "x" }, ctx).where, "Related people");
  assert.equal(describeIssue({ field: "unknown.path", message: "x" }, ctx).where, "unknown.path");
});
test("check results must be well-formed and consistent", () => {
  assert.deepEqual(readCheck({ ready: true, issues: [] }), { ready: true, issues: [] });
  assert.throws(() => readCheck({ ready: true, issues: [{ field: "a", message: "b" }] }));
  assert.throws(() => readCheck({ ready: false, issues: [] }));
  assert.throws(() => readCheck({ ready: "yes", issues: [] }));
  assert.throws(() => readCheck(null));
});
