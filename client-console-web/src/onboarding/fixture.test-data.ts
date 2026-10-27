import { readApplication, readSchema } from "./model.ts";

/** 与 AE 机构国家包同形的最小 Schema（字段键、分组、二选一组、地址组件均取自初始化 DML）。 */
export const schemaFixture = () => readSchema({
  schemaId: "9270000000700005", versionNo: 1,
  sections: [
    { section: "BASIC", labelKey: "onboardingSchema.section.BASIC", groups: [
      { group: "REGISTRATION", labelKey: "onboardingSchema.group.REGISTRATION", fields: [
        { fieldKey: "organization.legalName", widget: "TEXT", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "onboardingSchema.field.organization.legalName", order: 1010, validation: { maxLength: 128 } },
        { fieldKey: "organization.organizationType", widget: "SELECT", catalogCode: "ORGANIZATION_TYPE", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1030 },
        { fieldKey: "organization.registrationAuthorityCode", widget: "SELECT", catalogCode: "REGISTRATION_AUTHORITY", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1060,
          visibleWhen: "organization.organizationType=FZ_LLC|FZE" },
      ] },
      { group: "IDENTIFIERS", labelKey: "onboardingSchema.group.IDENTIFIERS", fields: [
        { fieldKey: "identifier.SA_CR_NUMBER", widget: "IDENTIFIER", requirementLevel: "REQUIRED", requirementGroup: "ORGANIZATION_ID", appliesTo: "PRIMARY", labelKey: "x", order: 1090 },
        { fieldKey: "identifier.SA_NATIONAL_ID", widget: "IDENTIFIER", requirementLevel: "REQUIRED", requirementGroup: "PERSON_ID", appliesTo: "RELATED_PERSON", labelKey: "x", order: 1300 },
        { fieldKey: "identifier.PASSPORT", widget: "IDENTIFIER", requirementLevel: "REQUIRED", requirementGroup: "PERSON_ID", appliesTo: "RELATED_PERSON", labelKey: "x", order: 1310 },
      ] },
      { group: "TAX", labelKey: "x", fields: [
        { fieldKey: "attributes.SA_VAT_NUMBER", widget: "TEXT", requirementLevel: "OPTIONAL", appliesTo: "PRIMARY", labelKey: "x", order: 1100, validation: { pattern: "3[0-9]{13}3" } },
      ] },
      { group: "ADDRESS", labelKey: "x", fields: [{ fieldKey: "address", widget: "ADDRESS", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1110 }] },
      { group: "CONTACT", labelKey: "x", fields: [{ fieldKey: "contact.email", widget: "EMAIL", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 1120 }] },
      { group: "IDENTITY", labelKey: "x", fields: [
        { fieldKey: "individual.givenName", widget: "TEXT", requirementLevel: "REQUIRED", appliesTo: "RELATED_PERSON", labelKey: "x", order: 1210 },
      ] },
    ] },
    { section: "PEOPLE", labelKey: "x", groups: [{ group: "PEOPLE", labelKey: "x", fields: [
      { fieldKey: "people", widget: "PERSON_LIST", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "x", order: 3200 }] }] },
    { section: "DECLARATIONS", labelKey: "x", groups: [{ group: "DECLARATIONS", labelKey: "x", fields: [
      { fieldKey: "declaration.INFORMATION_TRUE", widget: "DECLARATION", requirementLevel: "REQUIRED", appliesTo: "PRIMARY", labelKey: "onboardingSchema.declaration.INFORMATION_TRUE", order: 5380 }] }] },
  ],
  catalogs: { REGISTRATION_AUTHORITY: [{ itemCode: "AE_DMCC", labelKey: "x" }] },
  organizationTypes: [{ code: "LLC", labelKey: "onboardingSchema.organizationType.SA.LLC" }],
  identifierTypes: [
    { code: "SA_CR_NUMBER", partyType: "ORGANIZATION", labelKey: "x", pattern: "[0-9]{10}", checksumRule: "NONE", issuerScopeMode: "NONE" },
    { code: "SA_NATIONAL_ID", partyType: "INDIVIDUAL", labelKey: "x", pattern: "1[0-9]{9}", checksumRule: "LUHN", issuerScopeMode: "NONE" },
    { code: "PASSPORT", partyType: "INDIVIDUAL", labelKey: "x", pattern: "[A-Z0-9]{5,20}", checksumRule: "NONE", issuerScopeMode: "COUNTRY", expiryRequired: true },
  ],
  addressFormat: { code: "SA_NATIONAL_ADDRESS", components: [
    { component: "postalCode", required: true, labelKey: "x", order: 50 },
    { component: "buildingNumber", required: true, labelKey: "x", order: 10 },
    { component: "city", required: true, labelKey: "x", order: 40, catalogCode: "CITY" },
  ] },
  relationRules: { uboThresholdPct: 25, minSignatories: 1 },
  materials: [
    { requirementCode: "REGISTRATION", subjectRole: "PRIMARY", documentTypes: ["COMMERCIAL_REGISTRATION"], required: true },
    { requirementCode: "BANK", subjectRole: "PRIMARY", documentTypes: ["BANK_PROOF"], required: true, maxAgeDays: 180 },
    { requirementCode: "IDENTITY", subjectRole: "RELATED_PERSON", documentTypes: ["PASSPORT", "NATIONAL_ID_CARD"], required: true },
  ],
}, "unavailable");

export const applicationFixture = (overrides: Record<string, unknown> = {}) => readApplication({
  applicationId: "301", applicationNo: "ONB-1", clientType: "ORGANIZATION", subjectPartyId: "401", status: "DRAFT", savedAt: null, editable: true,
  jurisdictionCode: "SA", organizationTypeCode: "LLC", values: {}, people: [], relations: [], documents: [], declarations: [],
  requirementsStatus: "AVAILABLE", requirements: [], ...overrides,
});
