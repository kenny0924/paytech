import { test } from "node:test";
import assert from "node:assert/strict";
import { RequestFailure } from "../auth.ts";
import { translator } from "../i18n/index.ts";
import { applicationFixture, schemaFixture } from "./fixture.test-data.ts";
import { DECLARATION_DOCUMENT_OUTDATED, declarationBody, documentReadable, isDocumentOutdated, legalTextKey, paragraphs, thresholdKey } from "./declarations.ts";
import { errorText, requestErrorText } from "./draft.ts";
import { readSchema } from "./model.ts";
import { declarationState, declared, taskSummaries } from "./progress.ts";

const to = translator("onboarding");
const TERMS = "PLATFORM_TERMS_ACCEPTANCE", VERSION = "2026-10-draft";
const record = (declarationType: string, declaredValue: string, extra: Record<string, unknown> = {}) =>
  ({ declarationType, declaredValue, declaredAt: "2026-10-27T00:00:00Z", ...extra });

test("a confirmation made before the details changed is stale: shown unticked and counted as incomplete", () => {
  const schema = schemaFixture();
  const stale = applicationFixture({ declarations: [record("INFORMATION_TRUE", "TRUE", { draftRevision: "3", current: false })] });
  assert.equal(declarationState(stale, "INFORMATION_TRUE"), "stale");
  assert.equal(declared(stale, "INFORMATION_TRUE"), false);
  const summary = taskSummaries(stale, schema).find((item) => item.key === "declarations")!;
  assert.deepEqual([summary.done, summary.total], [0, 1]);
  assert.equal(taskSummaries(stale, schema).find((item) => item.key === "review")!.state, "locked");

  const current = applicationFixture({ declarations: [record("INFORMATION_TRUE", "TRUE", { draftRevision: "4", current: true })] });
  assert.equal(declarationState(current, "INFORMATION_TRUE"), "confirmed");
  assert.equal(taskSummaries(current, schema).find((item) => item.key === "declarations")!.done, 1);
});

test("declaration state: unticked is open; a server without `current` keeps the latest TRUE; the requirement list reports stale", () => {
  assert.equal(declarationState(applicationFixture({ declarations: [record("INFORMATION_TRUE", "FALSE", { current: true })] }), "INFORMATION_TRUE"), "open");
  assert.equal(declarationState(applicationFixture({ declarations: [record("INFORMATION_TRUE", "TRUE")] }), "INFORMATION_TRUE"), "confirmed");
  const pending = (errorCode: string) => applicationFixture({ requirements: [{ requirementKind: "DECLARATION", requirementKey: "declaration.INFORMATION_TRUE",
    subjectPartyId: "401", status: "CURRENTLY_DUE", awaitingActionFrom: "CLIENT", errorCode, remediationCode: "SIGN_DECLARATION", dueTime: null }] });
  assert.equal(declarationState(pending("DECLARATION_STALE"), "INFORMATION_TRUE"), "stale");
  assert.equal(declarationState(pending("DECLARATION_MISSING"), "INFORMATION_TRUE"), "open");
});

test("ticking a declaration with a document sends the version that was shown; one without a document sends none", () => {
  assert.deepEqual(declarationBody({ declarationType: TERMS, documentVersion: VERSION }, true),
    { declarationType: TERMS, declaredValue: "TRUE", termsVersion: VERSION });
  assert.deepEqual(declarationBody({ declarationType: TERMS, documentVersion: VERSION }, false),
    { declarationType: TERMS, declaredValue: "FALSE", termsVersion: VERSION });
  assert.deepEqual(declarationBody({ declarationType: "INFORMATION_TRUE", documentVersion: null }, true),
    { declarationType: "INFORMATION_TRUE", declaredValue: "TRUE" });
});

test("form-schema keeps each declaration's document version and the threshold comparison (absent means GTE)", () => {
  const base = { schemaId: "1", versionNo: 2, sections: [] };
  const schema = readSchema({ ...base, relationRules: { uboThresholdPct: "25", uboThresholdComparison: "GT" },
    declarations: [{ declarationType: TERMS, labelKey: "x", documentVersion: VERSION }, { declarationType: "INFORMATION_TRUE", labelKey: "x", documentVersion: "" }] }, "x");
  assert.deepEqual(schema.declarations.map((item) => item.documentVersion), [VERSION, null]);
  assert.equal(schema.relationRules.uboThresholdComparison, "GT");
  assert.equal(readSchema({ ...base, relationRules: { uboThresholdPct: 25 } }, "x").relationRules.uboThresholdComparison, "GTE");
  assert.equal(readSchema({ ...base, relationRules: null }, "x").relationRules.uboThresholdComparison, "GTE");
});

test("threshold wording follows the comparison: GTE says 'or more', GT says 'more than'", () => {
  assert.equal(to(thresholdKey("people.check_ubo", "GTE"), { threshold: 25, count: 1 }), "Everyone owning 25% or more is added (1)");
  assert.equal(to(thresholdKey("people.check_ubo", "GT"), { threshold: 25, count: 1 }), "Everyone owning more than 25% is added (1)");
  assert.match(to(thresholdKey("people.intro", "GT"), { threshold: 25 }), /owns more than 25%/);
  assert.match(to(thresholdKey("start.checklist_people", "GT"), { threshold: 10 }), /more than 10%/);
  assert.match(to(thresholdKey("start.checklist_people", "GTE"), { threshold: 10 }), /10% or more/);
});

test("draft legal texts are keyed by declaration type and version, and are marked as drafts", () => {
  assert.equal(legalTextKey(TERMS, VERSION, "body"), "legal.platform_terms_acceptance.2026_10_draft.body");
  for (const type of [TERMS, "PRIVACY_NOTICE_ACKNOWLEDGED"]) {
    const body = to(legalTextKey(type, VERSION, "body"));
    assert.ok(paragraphs(body).length >= 2, type);
    assert.match(paragraphs(body)[0], /^DRAFT/);
    assert.notEqual(to(legalTextKey(type, VERSION, "title")), "onboarding." + legalTextKey(type, VERSION, "title"));
  }
  assert.equal(to(legalTextKey(TERMS, "2027-01", "body")), "onboarding." + legalTextKey(TERMS, "2027-01", "body"), "unknown versions are not invented");
});

test("an outdated document version and a stale declaration have their own localized messages", () => {
  const byField = new RequestFailure(422, "detail", "req-1", { termsVersion: DECLARATION_DOCUMENT_OUTDATED }, "INVALID_PARAMETER",
    { errors: [{ field: "termsVersion", message: DECLARATION_DOCUMENT_OUTDATED }] });
  // client-master 的实际形状：顶层代码 + termsVersion 字段的中文说明（说明不是代码）。
  const byCode = new RequestFailure(422, "detail", "req-2", { termsVersion: "条款已更新，请重新阅读后确认" }, DECLARATION_DOCUMENT_OUTDATED,
    { code: DECLARATION_DOCUMENT_OUTDATED, errors: [{ field: "termsVersion", message: "条款已更新，请重新阅读后确认" }] });
  const other = new RequestFailure(422, "detail", "req-3", { declaredValue: "INVALID" }, "INVALID_PARAMETER", null);
  assert.ok(isDocumentOutdated(byField) && isDocumentOutdated(byCode) && !isDocumentOutdated(other));
  assert.equal(requestErrorText(byField), to("request_error.declaration_document_outdated"));
  assert.equal(requestErrorText(byCode), to("request_error.declaration_document_outdated"));
  assert.notEqual(errorText("DECLARATION_STALE"), to("error.generic"));
  assert.notEqual(errorText("DECLARATION_STALE"), errorText("DECLARATION_MISSING"));
});

test("a declaration whose document version has no bundled text cannot be accepted", () => {
  const translate = (key: string) => to(key);
  assert.equal(documentReadable({ declarationType: TERMS, documentVersion: VERSION }, translate), true);
  assert.equal(documentReadable({ declarationType: TERMS, documentVersion: "2027-01" }, translate), false);
  assert.equal(documentReadable({ declarationType: "PRIVACY_NOTICE_ACKNOWLEDGED", documentVersion: "2027-01" }, translate), false);
  assert.equal(documentReadable({ declarationType: "INFORMATION_TRUE", documentVersion: null }, translate), true, "no document, nothing to read");
  // 只有标题、没有正文也不算可读。
  const titleOnly = (key: string) => key.endsWith(".title") ? "Terms" : "onboarding." + key;
  assert.equal(documentReadable({ declarationType: TERMS, documentVersion: VERSION }, titleOnly), false);
});
