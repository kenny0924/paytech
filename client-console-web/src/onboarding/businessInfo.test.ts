import { test } from "node:test";
import assert from "node:assert/strict";
import { readSchema } from "./model.ts";
import { draftPatch, localIssues } from "./draft.ts";
import { fieldIssue, findField, integerRange, isVisible, joinCodeList, optionalNote, pathsHiddenByChange, splitCodeList, taskBlocks } from "./schema.ts";

/**
 * REQ-12 业务信息补采：client-master form-schema 同形的 BUSINESS 分区（v3 新增四个字段，取值与 technique 9.4 一致）
 * <li>目录项为 Owner 形状 `{code, labelKey}`；支付方式目录 COMMERCIAL_PAYMENT_METHOD 由 client-master 从 commercial 补全。</li>
 */
const DAYS_PATTERN = "^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$";
const businessSchema = (refundLevel: "EVENTUALLY" | "OPTIONAL") => readSchema({
  schemaId: "9281200000900301", versionNo: 3,
  sections: [{ section: "BUSINESS", labelKey: "onboardingSchema.section.BUSINESS", groups: [
    { group: "OPERATIONS", labelKey: "onboardingSchema.group.OPERATIONS", fields: [
      { fieldKey: "business.websiteUrl", widget: "URL", requirementLevel: "OPTIONAL", appliesTo: "PRIMARY", labelKey: "onboardingSchema.field.business.websiteUrl", order: 4010 },
      { fieldKey: "business.expectedPaymentMethods", widget: "MULTI_SELECT", catalogCode: "COMMERCIAL_PAYMENT_METHOD", requirementLevel: "REQUIRED", appliesTo: "PRIMARY",
        labelKey: "onboardingSchema.field.business.expectedPaymentMethods", helpKey: "onboardingSchema.help.business.expectedPaymentMethods", order: 4110,
        validation: { pattern: null, minLength: null, maxLength: 512, checksumRule: null } },
      { fieldKey: "business.refundPolicyUrl", widget: "URL", requirementLevel: refundLevel, appliesTo: "PRIMARY", labelKey: "onboardingSchema.field.business.refundPolicyUrl", order: 4120,
        validation: { pattern: "^https://\\S+$", minLength: null, maxLength: 2048, checksumRule: null } },
    ] },
    { group: "FULFILLMENT", labelKey: "onboardingSchema.group.FULFILLMENT", fields: [
      { fieldKey: "business.fulfillmentType", widget: "SELECT", catalogCode: "FULFILLMENT_TYPE", requirementLevel: "REQUIRED", appliesTo: "PRIMARY",
        labelKey: "onboardingSchema.field.business.fulfillmentType", order: 4130 },
      { fieldKey: "business.fulfillmentDays", widget: "TEXT", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", visibleWhen: "business.fulfillmentType=PRE_ORDER",
        labelKey: "onboardingSchema.field.business.fulfillmentDays", order: 4140, validation: { pattern: DAYS_PATTERN, minLength: null, maxLength: 3, checksumRule: null } },
    ] },
  ] }],
  catalogs: {
    COMMERCIAL_PAYMENT_METHOD: ["CARD", "APPLE_PAY", "GOOGLE_PAY", "STC_PAY"].map((code) => ({ code, labelKey: "onboardingSchema.paymentMethod." + code })),
    FULFILLMENT_TYPE: ["INSTANT", "PRE_ORDER", "SUBSCRIPTION", "SERVICE"].map((code) => ({ code, labelKey: "onboardingSchema.catalog.FULFILLMENT_TYPE." + code })),
  },
}, "unavailable");

const organization = businessSchema("EVENTUALLY");
const field = (key: string) => findField(organization, key)!;
const blocks = taskBlocks(organization, "profile", "PRIMARY");

test("form-schema with the MULTI_SELECT widget and the commercial payment-method catalog decodes in catalog order", () => {
  assert.equal(field("business.expectedPaymentMethods").widget, "MULTI_SELECT");
  assert.deepEqual(organization.catalogs.COMMERCIAL_PAYMENT_METHOD.map((item) => item.itemCode), ["CARD", "APPLE_PAY", "GOOGLE_PAY", "STC_PAY"]);
  assert.equal(organization.catalogs.COMMERCIAL_PAYMENT_METHOD[1].labelKey, "onboardingSchema.paymentMethod.APPLE_PAY");
  assert.deepEqual(blocks.map((block) => block.group.group), ["OPERATIONS", "FULFILLMENT"]);
});

test("code lists normalize like client-master CODE_LIST: trim, upper-case, drop empties, de-duplicate, sort ascending", () => {
  assert.deepEqual(splitCodeList(" card , apple_pay,CARD,, "), ["CARD", "APPLE_PAY"]);
  assert.equal(joinCodeList(splitCodeList(" card , apple_pay,CARD")), "APPLE_PAY,CARD");
  assert.equal(joinCodeList(["STC_PAY", "CARD", "APPLE_PAY"]), "APPLE_PAY,CARD,STC_PAY");
  assert.equal(joinCodeList([]), "");
  assert.deepEqual(splitCodeList(null), []);
  // 服务端读回的规范化值原样拆分，再次连接不变（往返稳定，不产生多余的草稿改动）。
  assert.equal(joinCodeList(splitCodeList("APPLE_PAY,CARD")), "APPLE_PAY,CARD");
});

test("multi-select format: codes only, at most 20; missing is reported only when continuing", () => {
  const methods = field("business.expectedPaymentMethods");
  assert.equal(fieldIssue(methods, "APPLE_PAY,CARD", true), null);
  assert.deepEqual(fieldIssue(methods, "CARD,1BAD", false), { key: "error.format" });
  assert.deepEqual(fieldIssue(methods, Array.from({ length: 21 }, (_, i) => "M" + i).join(","), false), { key: "error.format" });
  assert.equal(fieldIssue(methods, "", false), null);
  assert.deepEqual(localIssues(blocks, { "business.fulfillmentType": "INSTANT" }, organization, true)["business.expectedPaymentMethods"], { key: "error.field_missing" });
});

test("refund policy accepts only https links and says so", () => {
  const refund = field("business.refundPolicyUrl");
  assert.deepEqual(fieldIssue(refund, "http://gulfbytes.ae/refund", false), { key: "error.https_url" });
  assert.deepEqual(fieldIssue(refund, "gulfbytes.ae/refund", false), { key: "error.https_url" });
  assert.equal(fieldIssue(refund, "https://gulfbytes.ae/refund-policy", false), null);
  assert.deepEqual(fieldIssue(refund, "https://gulfbytes.ae/re fund", false), { key: "error.format" });
  // 不带 https 正则的网址字段仍按原规则（http 与 https 均可）。
  assert.equal(fieldIssue(field("business.websiteUrl"), "http://gulfbytes.ae", false), null);
});

test("an integer-range pattern is recognized from the Schema and drives the 1–365 message", () => {
  assert.deepEqual(integerRange(DAYS_PATTERN), { min: 1, max: 365 });
  assert.equal(integerRange("[0-9]{10}"), null, "fixed-length numbers are not a range");
  assert.equal(integerRange("3[0-9]{13}3"), null);
  assert.equal(integerRange("^https://\\S+$"), null);
  assert.equal(integerRange("[0-9]{1,4}"), null, "unbounded within the probe is not a range");
  assert.equal(integerRange("("), null);
  const days = field("business.fulfillmentDays");
  for (const value of ["400", "0", "abc", "-3", "1.5"]) assert.deepEqual(fieldIssue(days, value, false), { key: "error.days_range", params: { min: 1, max: 365 } }, value);
  for (const value of ["1", "30", "365"]) assert.equal(fieldIssue(days, value, false), null, value);
});

test("days are shown and required only for pre-order", () => {
  const days = field("business.fulfillmentDays");
  assert.equal(isVisible(days, {}, organization), false);
  assert.equal(isVisible(days, { "business.fulfillmentType": "INSTANT" }, organization), false);
  assert.equal(isVisible(days, { "business.fulfillmentType": "PRE_ORDER" }, organization), true);
  const base = { "business.expectedPaymentMethods": "CARD" };
  assert.deepEqual(localIssues(blocks, { ...base, "business.fulfillmentType": "PRE_ORDER" }, organization, true)["business.fulfillmentDays"], { key: "error.field_missing" });
  assert.equal(localIssues(blocks, { ...base, "business.fulfillmentType": "SERVICE" }, organization, true)["business.fulfillmentDays"], undefined);
});

test("refund policy is never reported missing before review: EVENTUALLY (organization) and OPTIONAL (individual)", () => {
  const values = { "business.expectedPaymentMethods": "CARD", "business.fulfillmentType": "INSTANT" };
  assert.deepEqual(localIssues(blocks, values, organization, true), {});
  const individual = businessSchema("OPTIONAL");
  assert.deepEqual(localIssues(taskBlocks(individual, "profile", "PRIMARY"), values, individual, true), {});
});

test("leaving pre-order clears the hidden days in the same draft change; entering it clears nothing", () => {
  const preOrder = { "business.fulfillmentType": "PRE_ORDER", "business.fulfillmentDays": "30" };
  const instant = { ...preOrder, "business.fulfillmentType": "INSTANT" };
  assert.deepEqual(pathsHiddenByChange(organization, preOrder, instant), ["business.fulfillmentDays"]);
  assert.deepEqual(pathsHiddenByChange(organization, instant, preOrder), []);
  assert.deepEqual(pathsHiddenByChange(organization, { "business.fulfillmentType": "PRE_ORDER" }, { "business.fulfillmentType": "INSTANT" }), [], "nothing to clear");
  const body = draftPatch(preOrder, { "business.fulfillmentType": "INSTANT", "business.fulfillmentDays": "" }, new Set());
  assert.deepEqual(body.dirtyFields.sort(), ["business.fulfillmentDays", "business.fulfillmentType"]);
  assert.deepEqual(body.values, { business: { fulfillmentType: "INSTANT", fulfillmentDays: null } });
});

test("a normalized multi-select value is sent as one comma string", () => {
  const body = draftPatch({}, { "business.expectedPaymentMethods": joinCodeList(["CARD", "APPLE_PAY"]) }, new Set());
  assert.deepEqual(body.values, { business: { expectedPaymentMethods: "APPLE_PAY,CARD" } });
});

test("only fulfillment days are cleared when hidden: the AE_OTHER authority name keeps its value as before", () => {
  const registration = readSchema({ schemaId: "9281200000900301", versionNo: 3, sections: [{ section: "BASIC", labelKey: "x", groups: [
    { group: "REGISTRATION", labelKey: "x", fields: [
      { fieldKey: "organization.registrationAuthorityCode", widget: "SELECT", catalogCode: "REGISTRATION_AUTHORITY", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1060 },
      { fieldKey: "attributes.AE_REGISTRATION_AUTHORITY_NAME", widget: "TEXT", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1070,
        visibleWhen: "organization.registrationAuthorityCode=AE_OTHER", validation: { maxLength: 128 } }] }] }, ...organization.sections],
    catalogs: { REGISTRATION_AUTHORITY: [{ code: "AE_DED", labelKey: "x" }, { code: "AE_OTHER", labelKey: "x" }], ...organization.catalogs } }, "unavailable");
  const other = { "organization.registrationAuthorityCode": "AE_OTHER", "attributes.AE_REGISTRATION_AUTHORITY_NAME": "Ras Al Khaimah Economic Zone" };
  const switched = { ...other, "organization.registrationAuthorityCode": "AE_DED" };
  assert.equal(isVisible(findField(registration, "attributes.AE_REGISTRATION_AUTHORITY_NAME")!, switched, registration), false);
  assert.deepEqual(pathsHiddenByChange(registration, other, switched), [], "the hidden authority name is not cleared");
  assert.deepEqual(draftPatch(other, switched, new Set()).values, { organization: { registrationAuthorityCode: "AE_DED" } });
  // 同一 Schema 中离开预售仍清空天数，并以 null 随交付方式一起发送。
  const preOrder = { ...switched, "business.fulfillmentType": "PRE_ORDER", "business.fulfillmentDays": "30" };
  const instant = { ...preOrder, "business.fulfillmentType": "INSTANT" };
  const cleared = pathsHiddenByChange(registration, preOrder, instant);
  assert.deepEqual(cleared, ["business.fulfillmentDays"]);
  const body = draftPatch(preOrder, { ...instant, ...Object.fromEntries(cleared.map((path) => [path, ""])) }, new Set());
  assert.deepEqual(body.values, { business: { fulfillmentType: "INSTANT", fulfillmentDays: null } });
});

test("an EVENTUALLY field is marked 'due before approval' instead of 'optional'; OPTIONAL stays optional; REQUIRED has no mark", () => {
  const lookup = (key: string) => key === "onboardingSchema.requirementStatus.EVENTUALLY_DUE" ? "Due before approval" : null;
  assert.equal(optionalNote(field("business.refundPolicyUrl"), lookup), "Due before approval");
  assert.equal(optionalNote(findField(businessSchema("OPTIONAL"), "business.refundPolicyUrl")!, lookup), true);
  assert.equal(optionalNote(field("business.expectedPaymentMethods"), lookup), false);
  assert.equal(optionalNote(field("business.refundPolicyUrl"), () => null), true, "falls back to 'optional' without text");
});
