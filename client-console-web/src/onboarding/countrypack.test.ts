import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { readApplication, readSchema } from "./model.ts";
import { availableTasks, draftPath, isIdentifierLead, selectedIdentifier, taskBlocks } from "./schema.ts";
import { localIssues } from "./draft.ts";
import { personSlots } from "./people.ts";
import { REF_COUNTRY_SUBDIVISION, REF_CURRENCY, countryOptions, currencyOptions, subdivisionOptions } from "./refdata.ts";

/** Platform 参考数据夹具（BFF 补全的形状）：ACTIVE 国家、币种与 AE / SA / EG 的部分一级行政区。 */
const referenceFixture = {
  countries: [
    { code: "AE", name: "United Arab Emirates", logoPublicUrl: "https://cdn.example.test/flags/ae.svg", callingCode: "971" },
    { code: "EG", name: "Egypt", logoPublicUrl: null, callingCode: "20" },
    { code: "GB", name: "United Kingdom", logoPublicUrl: null, callingCode: "44" },
    { code: "SA", name: "Saudi Arabia", logoPublicUrl: null, callingCode: "966" },
  ],
  [REF_CURRENCY]: ["AED", "EGP", "SAR", "USD"].map((code) => ({ code, labelKey: null, name: code + " name", logoPublicUrl: null, countryCode: null })),
  [REF_COUNTRY_SUBDIVISION]: [["AE-AZ", "AE", "Abu Dhabi"], ["AE-DU", "AE", "Dubai"], ["EG-C", "EG", "Cairo"], ["SA-01", "SA", "Riyadh"]]
    .map(([code, countryCode, name]) => ({ code, labelKey: null, name, logoPublicUrl: null, countryCode })),
};
/** 与 GetClientFormSchemaProcessor 一致：补国家列表；Schema 引用的 REF_ 目录才补条目。 */
function withReferenceData(owner: ReturnType<typeof ownerResponse>) {
  const referenced = new Set(JSON.stringify(owner).match(/"catalogCode":"REF_[A-Z_]+"/g)?.map((item) => item.split(":")[1].replaceAll('"', "")) ?? []);
  const catalogs: Record<string, unknown> = { ...owner.catalogs };
  for (const code of [REF_CURRENCY, REF_COUNTRY_SUBDIVISION] as const) if (referenced.has(code)) catalogs[code] = referenceFixture[code];
  return { ...owner, catalogs, countries: referenceFixture.countries };
}

/**
 * 用仓库内国家包 DML 组装与 client-master FormSchemaConverter 同形的 form-schema 响应（含 organizationTypeCode "*"、
 * 目录项 code、规则数值字符串、个人主体 relationRules=null），再按 client-console-bff 的方式补上 Platform 参考数据
 * （countries 与 REF_ 目录，数据为测试夹具），核对 Web 解码与渲染前置逻辑都能处理真实形状。
 */
const repo = fileURLToPath(new URL("../../../", import.meta.url));
const read = (path: string) => { try { return readFileSync(join(repo, path), "utf8"); } catch { return null; } };

type Row = Record<string, string | number | null>;
function parseInserts(sql: string): Record<string, Row[]> {
  const tables: Record<string, Row[]> = {};
  const re = /INSERT INTO `?(\w+)`?\s*\(([^)]*)\)\s*VALUES\s*/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(sql))) {
    const table = match[1], cols = match[2].split(",").map((c) => c.trim().replace(/`/g, ""));
    let i = re.lastIndex;
    const rows = tables[table] ??= [];
    while (sql[i] === "(" || /\s|,/.test(sql[i])) {
      if (sql[i] !== "(") { i++; continue; }
      i++;
      const values: (string | number | null)[] = [];
      let token = "", quoted = false, wasQuoted = false, depth = 0;
      for (; i < sql.length; i++) {
        const c = sql[i];
        if (quoted) { if (c === "'" && sql[i + 1] === "'") { token += "'"; i++; } else if (c === "'") quoted = false; else token += c; continue; }
        if (c === "'") { quoted = true; wasQuoted = true; token = ""; continue; }
        if (c === "(") { depth++; token += c; continue; }
        if (c === ")" && depth > 0) { depth--; token += c; continue; }
        if (c === "," || c === ")") {
          const raw = token.trim();
          values.push(wasQuoted ? token : raw === "NULL" ? null : /^-?\d{1,15}$/.test(raw) ? Number(raw) : raw);
          token = ""; wasQuoted = false;
          if (c === ")") { i++; break; }
          continue;
        }
        token += c;
      }
      rows.push(Object.fromEntries(cols.map((col, index) => [col, values[index]])));
      while (i < sql.length && /\s/.test(sql[i])) i++;
      if (sql[i] === ";") break;
    }
    re.lastIndex = i;
  }
  return tables;
}

const cmSql = read("paytech/client-master/docs/database/sql/client-master-initial-data.sql");
const cpSql = read("paytech/compliance/docs/database/sql/compliance-initial-data.sql");
const cm = cmSql ? parseInserts(cmSql) : null, cp = cpSql ? parseInserts(cpSql) : null;
const rule = (scope: string, code: string, ruleCode: string) =>
  cm!.t_rule_setting.find((r) => r.scope_type === scope && r.scope_code === code && r.rule_code === ruleCode)?.rule_value ?? null;

function materials(jurisdiction: string, partyType: string, roleType: string, subjectRole: string) {
  const program = cp!.t_dd_program.find((p) => p.jurisdiction_code === jurisdiction && p.party_type === partyType && p.role_type === roleType);
  if (!program) return [];
  const version = cp!.t_dd_program_version.find((v) => String(v.program_id) === String(program.program_id))!;
  return cp!.t_dd_program_requirement.filter((r) => String(r.program_version_id) === String(version.program_version_id)).map((r) => ({
    requirementCode: r.requirement_code, subjectRole, required: Number(r.required_flag) === 1, maxAgeDays: r.max_age_days,
    documentTypes: cp!.t_dd_program_requirement_doc_type.filter((d) => String(d.program_requirement_id) === String(r.program_requirement_id)).map((d) => d.document_type),
  }));
}

/** 与 FormSchemaConverter.convert 同形。 */
function ownerResponse(jurisdiction: string, partyType: string, organizationType: string) {
  const j = cm!.t_jurisdiction.find((row) => row.jurisdiction_code === jurisdiction)!;
  const schema = cm!.t_onboarding_schema.find((s) => s.jurisdiction_code === jurisdiction && s.party_type === partyType && s.organization_type_code === organizationType)
    ?? cm!.t_onboarding_schema.find((s) => s.jurisdiction_code === jurisdiction && s.party_type === partyType && s.organization_type_code === "*")!;
  const fields = cm!.t_onboarding_schema_field.filter((f) => String(f.schema_id) === String(schema.schema_id) && f.requirement_level !== "HIDDEN")
    .sort((a, b) => Number(a.display_order) - Number(b.display_order));
  const sections: { section: unknown; labelKey: string; groups: { group: unknown; labelKey: string; fields: unknown[] }[] }[] = [];
  for (const f of fields) {
    let section = sections.find((s) => s.section === f.section);
    if (!section) sections.push(section = { section: f.section, labelKey: "onboardingSchema.section." + f.section, groups: [] });
    let group = section.groups.find((g) => g.group === f.display_group);
    if (!group) section.groups.push(group = { group: f.display_group, labelKey: "onboardingSchema.group." + f.display_group, fields: [] });
    group.fields.push({ fieldKey: f.field_key, widget: f.widget_code, catalogCode: f.catalog_code, requirementLevel: f.requirement_level,
      requirementGroup: f.requirement_group, requirementGroupLabelKey: f.requirement_group ? "onboardingSchema.requirementGroup." + f.requirement_group : null,
      appliesTo: f.applies_to_subject, validation: { pattern: f.validation_pattern, minLength: f.min_length, maxLength: f.max_length, checksumRule: null },
      visibleWhen: f.visible_when, labelKey: f.label_key, helpKey: f.help_key, order: f.display_order });
  }
  const catalogs: Record<string, { code: unknown; labelKey: unknown }[]> = {};
  for (const item of cm!.t_catalog_item.filter((c) => !c.jurisdiction_code || c.jurisdiction_code === jurisdiction))
    (catalogs[String(item.catalog_code)] ??= []).push({ code: item.item_code, labelKey: item.label_key });
  const individual = new Set(cm!.t_rule_setting.filter((r) => r.rule_code === "PARTY_TYPE" && r.rule_value === "INDIVIDUAL").map((r) => r.scope_code));
  const address = (code: unknown) => ({ code, components: cm!.t_address_format_component.filter((c) => c.address_format_code === code)
    .map((c) => ({ component: c.component, required: Number(c.required_flag) === 1, catalogCode: c.catalog_code, labelKey: c.label_key, order: c.display_order })) });
  const scope = jurisdiction + ":" + organizationType;
  return {
    schemaId: String(schema.schema_id), versionNo: schema.version_no, jurisdictionCode: jurisdiction, partyType,
    organizationTypeCode: schema.organization_type_code, sections, catalogs,
    organizationTypes: cm!.t_organization_type.filter((o) => o.jurisdiction_code === jurisdiction
      && (partyType === "INDIVIDUAL") === individual.has(jurisdiction + ":" + o.type_code)).map((o) => ({ code: o.type_code, labelKey: o.label_key })),
    identifierTypes: cm!.t_identifier_type.filter((t) => !t.jurisdiction_code || t.jurisdiction_code === jurisdiction).map((t) => ({
      code: t.identifier_type_code, partyType: t.party_type, labelKey: t.label_key, pattern: t.format_regex, checksumRule: t.checksum_rule,
      issuerScopeMode: t.issuer_scope_mode, expiryRequired: rule("IDENTIFIER_TYPE", String(t.identifier_type_code), "EXPIRY_REQUIRED") === "1", acquireMode: null })),
    addressFormat: address(j.address_format_code), fallbackAddressFormat: address("GENERIC"),
    relationTypes: partyType === "ORGANIZATION" ? cm!.t_relation_type.map((r) => ({ code: r.relation_type_code, ruleRole: r.rule_role,
      uboFlag: Number(r.ubo_flag) === 1, ownershipPctRequired: Number(r.ownership_pct_required) === 1, labelKey: r.label_key })) : [],
    relationRules: partyType === "ORGANIZATION" ? { uboThresholdPct: rule("JURISDICTION", jurisdiction, "UBO_THRESHOLD_PCT"),
      uboFallbackRule: rule("JURISDICTION", jurisdiction, "UBO_FALLBACK_RULE"), controlPersonRequired: rule("JURISDICTION", jurisdiction, "CONTROL_PERSON_REQUIRED") === "1",
      minSignatories: Number(rule("ORGANIZATION_TYPE", scope, "MIN_SIGNATORIES") ?? 0), requiresManager: rule("ORGANIZATION_TYPE", scope, "REQUIRES_MANAGER") === "1",
      requiresDirectors: rule("ORGANIZATION_TYPE", scope, "REQUIRES_DIRECTORS") === "1", uboCollectionMode: rule("ORGANIZATION_TYPE", scope, "UBO_COLLECTION_MODE") } : null,
    materials: [...materials(jurisdiction, partyType, "CLIENT", "PRIMARY"),
      ...(partyType === "ORGANIZATION" ? materials(jurisdiction, "INDIVIDUAL", "RELATED_PERSON", "ANY_RELATED") : [])],
    declarations: fields.filter((f) => f.widget_code === "DECLARATION").map((f) => ({
      declarationType: String(f.field_key).split(".")[1], labelKey: f.label_key, requirementLevel: f.requirement_level })),
  };
}

const cases: [string, string, string][] = [["AE", "ORGANIZATION", "*"], ["AE", "ORGANIZATION", "FZ_LLC"], ["SA", "ORGANIZATION", "*"],
  ["EG", "ORGANIZATION", "*"], ["AE", "INDIVIDUAL", "*"], ["AE", "INDIVIDUAL", "SOLE_ESTABLISHMENT"], ["SA", "INDIVIDUAL", "*"], ["EG", "INDIVIDUAL", "*"]];

for (const [jurisdiction, partyType, organizationType] of cases) {
  test("real-shaped form-schema decodes and drives the form: " + jurisdiction + " " + partyType + " " + organizationType, () => {
    assert.ok(cm && cp, "country-pack DML not found");
    const raw = withReferenceData(ownerResponse(jurisdiction, partyType, organizationType));
    const schema = readSchema(JSON.parse(JSON.stringify(raw)), "unavailable");
    assert.ok(schema.sections.length > 0);
    assert.ok(schema.addressFormat && schema.addressFormat.components.length > 0);
    assert.ok(Object.values(schema.catalogs).every((items) => items.every((item) => item.itemCode && item.itemCode !== "*")));
    const tasks = availableTasks(schema);
    for (const task of ["profile", "address", "materials", "declarations", "review"]) assert.ok(tasks.includes(task as never), task);
    assert.equal(tasks.includes("people"), partyType === "ORGANIZATION");
    if (partyType === "ORGANIZATION") {
      assert.ok(Number.isFinite(schema.relationRules.uboThresholdPct));
      assert.ok(personSlots(schema, ["UBO"]).length > 0, "related-person ID slots");
    }
    for (const subject of ["PRIMARY", "RELATED_PERSON"] as const) {
      for (const block of taskBlocks(schema, "profile", subject).concat(taskBlocks(schema, "address", subject)))
        for (const field of block.fields) {
          assert.ok(field.widget === "ADDRESS" || draftPath(field), field.fieldKey);
          if (field.widget === "IDENTIFIER" && isIdentifierLead(field, schema)) assert.ok(selectedIdentifier(field, schema, {}), field.fieldKey);
        }
    }
    const blocks = taskBlocks(schema, "profile", "PRIMARY");
    assert.ok(Object.keys(localIssues(blocks, {}, schema, true)).length > 0);
  });
}

test("application read with organizationTypeCode '*' or null decodes", () => {
  for (const organizationTypeCode of ["*", null]) {
    const app = readApplication({ applicationId: "1", applicationNo: "A", clientType: "INDIVIDUAL", subjectPartyId: "2", status: "DRAFT", editable: true,
      jurisdictionCode: "AE", organizationTypeCode, schemaId: "9270000000700003", values: {}, people: [], relations: [], documents: [], declarations: [] });
    assert.equal(app.jurisdictionCode, "AE");
  }
});

test("AE application, person living in GB: the drawer address switches to the generic format and validates it", async () => {
  const { activeAddressFormat, addressPathsToClear } = await import("./schema.ts");
  assert.ok(cm && cp, "country-pack DML not found");
  const schema = readSchema(JSON.parse(JSON.stringify(withReferenceData(ownerResponse("AE", "ORGANIZATION", "*")))), "x");
  assert.equal(activeAddressFormat(schema, {})?.code, "AE_POBOX");
  assert.equal(activeAddressFormat(schema, { "address.countryCode": "AE" })?.code, "AE_POBOX");
  const before = { "address.countryCode": "AE", "address.addressLine1": "Unit 1", "address.city": "Dubai", "address.stateOrProvince": "AE-DU", "address.poBox": "340512" };
  const after = { ...before, "address.countryCode": "GB" };
  assert.equal(activeAddressFormat(schema, after)?.code, "GENERIC");
  // PO Box 只在阿联酋格式里，切到通用格式时清空；已选的阿联酋酋长国属于原国家，也清空；两边都有的其他组件保留。
  assert.deepEqual(addressPathsToClear(schema, before, after).sort(), ["address.poBox", "address.stateOrProvince"]);
  const person = taskBlocks(schema, "people", "RELATED_PERSON");
  const issues = localIssues(person, { "address.countryCode": "GB", "address.addressLine1": "1 High St" }, schema, true);
  assert.ok(issues["address.city"], "generic city required");
  assert.equal(issues["address.poBox"], undefined);
  assert.equal(issues["address.stateOrProvince"], undefined, "GENERIC state is optional");
});

test("SA application with an AE business address uses the generic format; switching back restores the national layout", async () => {
  const { activeAddressFormat, addressPathsToClear } = await import("./schema.ts");
  assert.ok(cm && cp, "country-pack DML not found");
  const schema = readSchema(JSON.parse(JSON.stringify(withReferenceData(ownerResponse("SA", "ORGANIZATION", "*")))), "x");
  assert.equal(activeAddressFormat(schema, { "address.countryCode": "SA" })?.code, "SA_NATIONAL_ADDRESS");
  const saAddress = { "address.countryCode": "SA", "address.buildingNumber": "2929", "address.addressLine1": "Rayhanah Bint Zaid",
    "address.district": "Al Arid", "address.city": "RIYADH", "address.postalCode": "13337", "address.additionalNumber": "8118" };
  const abroad = { ...saAddress, "address.countryCode": "AE" };
  assert.equal(activeAddressFormat(schema, abroad)?.code, "GENERIC");
  assert.deepEqual(addressPathsToClear(schema, saAddress, abroad).sort(), ["address.additionalNumber", "address.buildingNumber", "address.district"]);
  const blocks = taskBlocks(schema, "address", "PRIMARY");
  const issues = localIssues(blocks, { "address.countryCode": "AE", "address.addressLine1": "Office 5", "address.city": "Dubai" }, schema, true);
  assert.equal(issues["address.buildingNumber"], undefined);
  assert.equal(issues["address.postalCode"], undefined, "GENERIC postal code is optional");
  assert.deepEqual(addressPathsToClear(schema, abroad, saAddress), []);
});

test("country packs reference only the Platform catalogs the BFF fills, and the form resolves them from injected reference data", () => {
  assert.ok(cm && cp, "country-pack DML not found");
  const referenced = new Set([...cm!.t_onboarding_schema_field.map((f) => f.catalog_code), ...cm!.t_address_format_component.map((c) => c.catalog_code)]
    .filter((code): code is string => typeof code === "string" && code.startsWith("REF_")));
  assert.deepEqual([...referenced].sort(), [REF_COUNTRY_SUBDIVISION, REF_CURRENCY].sort());
  assert.ok(cm!.t_catalog_item.every((item) => !/^(REF_|INDUSTRY$|CURRENCY$|STATE_OR_PROVINCE$)/.test(String(item.catalog_code))),
    "reference data must not live in t_catalog_item");
  const schema = readSchema(JSON.parse(JSON.stringify(withReferenceData(ownerResponse("AE", "ORGANIZATION", "*")))), "x");
  const state = schema.addressFormat!.components.find((component) => component.component === "stateOrProvince")!;
  assert.equal(state.catalogCode, REF_COUNTRY_SUBDIVISION);
  assert.deepEqual(subdivisionOptions(schema.catalogs[REF_COUNTRY_SUBDIVISION], "AE", "en", () => null).map((option) => option.code), ["AE-AZ", "AE-DU"]);
  assert.deepEqual(currencyOptions(schema.catalogs[REF_CURRENCY], "en").map((option) => option.code), ["AED", "EGP", "SAR", "USD"]);
  assert.deepEqual(countryOptions(schema.countries, "en").map((option) => option.code), ["EG", "SA", "AE", "GB"]);
});
