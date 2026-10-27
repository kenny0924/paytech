import { ApiError } from '../api/session.ts';

/**
 * 入驻配置（只读）：辖区包、采集 Schema、目录项（client-master CM-10）与尽调程序（compliance O14）的 BFF 路径与页面模型
 * <li>后端 label_key 为 `onboardingSchema.x`，对应 `onboarding-schema` 命名空间的 `onboarding-schema.x`；没有译文时显示代码，不推断名称。</li>
 * <li>显示名为 `client-review.config.enum.<type>.<code>`（status、acceptancePolicy、partyType、roleType、requirementLevel、widget）。</li>
 */
export const configApi = '/onboarding-config-admin/v1';
export const configRoute = '/client-onboarding/config';

export interface RuleSetting { scopeType: string; scopeCode: string; ruleCode: string; ruleValue: string }
export interface Jurisdiction {
  jurisdictionCode: string; countryCode: string; labelKey: string | null; addressFormatCode: string | null; status: string;
  acceptancePolicy: string; ruleSettings: RuleSetting[];
}
export interface SchemaField {
  section: string; group: string | null; fieldKey: string; widget: string; catalogCode: string | null; requirementLevel: string;
  requirementGroup: string | null; appliesTo: string | null; visibleWhen: string | null; labelKey: string | null; order: number;
}
export interface OnboardingSchema {
  schemaId: string; jurisdictionCode: string; partyType: string; roleType: string | null; organizationTypeCode: string | null; versionNo: number;
  status: string; fields: SchemaField[];
}
export interface CatalogItem { catalogCode: string; itemCode: string; jurisdictionCode: string | null; labelKey: string | null; clientTypes: string | null; displayOrder: number; status: string }
/** 程序中的一项：材料、检查或筛查。 */
export interface ProgramItem { kind: 'MATERIAL' | 'CHECK' | 'SCREENING'; code: string; subjectRole: string | null; required: boolean; detail: string[] }
/** 一个程序版本（compliance 列出全部版本，含 DRAFT / RETIRED）。 */
export interface Program {
  programId: string; programCode: string; programVersionId: string; versionNo: number; qualificationType: string | null; jurisdictionCode: string | null;
  tier: string | null; ownerPartnerId: string | null; reviewIntervalMonths: number | null; fourEyesRequired: boolean; partyType: string | null;
  roleType: string | null; programStatus: string; versionStatus: string; approvedTime: string | null; items: ProgramItem[];
}

const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const nullableText = (v: unknown, max = 255) => v === null || text(v, max);
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const nullableCode = (v: unknown) => v === null || code(v);
const jurisdiction = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9-]{1,15}$/.test(v);
const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const int = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;
const list = (v: unknown, max = 2000): v is unknown[] => Array.isArray(v) && v.length <= max;
const opt = <T>(v: T | undefined, fallback: T): T => v === undefined ? fallback : v;
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
/** BFF 列表：client-master 配置接口返回数组，compliance 程序为 {items}。 */
function items(value: unknown, max = 2000): unknown[] {
  if (Array.isArray(value)) { require(list(value, max)); return value; }
  const v = value as { items: unknown }; require(v && list(v.items, max)); return v.items;
}
/** client-master 的通配值（OnboardingPack.ANY）：目录项辖区与 Schema 组织类型为 * 表示全局 / 默认。 */
const WILDCARD = '*';
/** 受理策略来自辖区级规则开关 ACCEPTANCE_POLICY（OPEN / ENHANCED_ONLY / CLOSED）；没有该规则即内置默认 OPEN。 */
const ACCEPTANCE_POLICY = 'ACCEPTANCE_POLICY';
const DEFAULT_ACCEPTANCE_POLICY = 'OPEN';

export function decodeJurisdictions(value: unknown): Jurisdiction[] {
  const result = items(value, 300).map(raw => {
    const v = raw as Record<string, unknown>; const rules = opt(v?.rules as unknown[] | undefined, []);
    require(v && jurisdiction(v.jurisdictionCode) && typeof v.countryCode === 'string' && /^[A-Z]{2}$/.test(v.countryCode)
      && nullableText(opt(v.labelKey as string | null | undefined, null), 128) && nullableCode(opt(v.addressFormatCode as string | null | undefined, null))
      && code(v.status) && list(rules, 500));
    const ruleSettings = rules.map(raw => { const r = raw as RuleSetting & { status?: string };
      require(r && code(r.scopeType) && text(r.scopeCode, 64) && code(r.ruleCode) && text(r.ruleValue, 64) && (r.status === undefined || code(r.status)));
      return { scopeType: r.scopeType, scopeCode: r.scopeCode, ruleCode: r.ruleCode, ruleValue: r.ruleValue }; });
    const policy = ruleSettings.find(r => r.scopeType === 'JURISDICTION' && r.scopeCode === v.jurisdictionCode && r.ruleCode === ACCEPTANCE_POLICY)?.ruleValue
      ?? DEFAULT_ACCEPTANCE_POLICY;
    return { jurisdictionCode: v.jurisdictionCode as string, countryCode: v.countryCode as string, labelKey: opt(v.labelKey as string | null | undefined, null),
      addressFormatCode: opt(v.addressFormatCode as string | null | undefined, null), status: v.status as string, acceptancePolicy: policy, ruleSettings };
  });
  require(new Set(result.map(j => j.jurisdictionCode)).size === result.length);
  return result;
}
/** client-master 字段名：displayGroup、widgetCode、appliesToSubject、displayOrder。 */
function decodeField(raw: unknown): SchemaField {
  const v = raw as Record<string, unknown>;
  const field: SchemaField = { section: v?.section as string, group: opt(v?.displayGroup as string | null | undefined, null), fieldKey: v?.fieldKey as string,
    widget: v?.widgetCode as string, catalogCode: opt(v?.catalogCode as string | null | undefined, null), requirementLevel: v?.requirementLevel as string,
    requirementGroup: opt(v?.requirementGroup as string | null | undefined, null), appliesTo: opt(v?.appliesToSubject as string | null | undefined, null),
    visibleWhen: opt(v?.visibleWhen as string | null | undefined, null), labelKey: opt(v?.labelKey as string | null | undefined, null), order: v?.displayOrder as number };
  require(v && code(field.section) && nullableText(field.group, 64) && text(field.fieldKey, 128) && code(field.widget) && nullableCode(field.catalogCode)
    && code(field.requirementLevel) && nullableText(field.requirementGroup, 64) && nullableText(field.appliesTo, 128) && nullableText(field.visibleWhen, 512)
    && nullableText(field.labelKey, 128) && int(field.order));
  return field;
}
/**
 * 某辖区的采集 Schema；client-master 以 organizationTypeCode `*` 表示“该主体类型所有组织类型的默认 Schema”，页面统一为 null
 */
export function decodeSchemas(value: unknown, jurisdictionCode: string): OnboardingSchema[] {
  return items(value, 200).map(raw => {
    const v = raw as OnboardingSchema;
    if (v && v.organizationTypeCode === WILDCARD) v.organizationTypeCode = null;
    require(v && isId(v.schemaId) && v.jurisdictionCode === jurisdictionCode && code(v.partyType) && nullableCode(opt(v.roleType, null))
      && nullableCode(opt(v.organizationTypeCode, null)) && int(v.versionNo) && code(v.status) && list(v.fields, 1000));
    return { schemaId: v.schemaId, jurisdictionCode: v.jurisdictionCode, partyType: v.partyType, roleType: opt(v.roleType, null),
      organizationTypeCode: opt(v.organizationTypeCode, null), versionNo: v.versionNo, status: v.status,
      fields: v.fields.map(decodeField).sort((a, b) => a.order - b.order) };
  });
}

export function decodeCatalogItems(value: unknown, catalogCode: string): CatalogItem[] {
  return items(value).map(raw => {
    const v = raw as CatalogItem;
    if (v && v.jurisdictionCode === WILDCARD) v.jurisdictionCode = null;
    require(v && v.catalogCode === catalogCode && text(v.itemCode, 64) && (opt(v.jurisdictionCode, null) === null || jurisdiction(v.jurisdictionCode))
      && nullableText(opt(v.labelKey, null), 128) && nullableText(opt(v.clientTypes, null), 64) && int(v.displayOrder) && code(v.status));
    return { catalogCode: v.catalogCode, itemCode: v.itemCode, jurisdictionCode: opt(v.jurisdictionCode, null), labelKey: opt(v.labelKey, null),
      clientTypes: opt(v.clientTypes, null), displayOrder: v.displayOrder, status: v.status };
  }).sort((a, b) => a.displayOrder - b.displayOrder || a.itemCode.localeCompare(b.itemCode));
}
type Wire = Record<string, unknown>;
const bool = (v: unknown, fallback: boolean) => v === undefined || v === null ? fallback : v;
/** 材料 / 检查 / 筛查展平为一张表；detail 为代码化的附加事实（文件类型、时效、模式等），由页面翻译。 */
function programItems(v: Wire): ProgramItem[] {
  const materials = opt(v.materials as Wire[] | undefined, []); const checks = opt(v.checks as Wire[] | undefined, []);
  const screenings = opt(v.screenings as Wire[] | undefined, []);
  require(list(materials, 200) && list(checks, 200) && list(screenings, 200));
  const role = (x: Wire) => { const r = opt(x.subjectRole as string | null | undefined, null); require(nullableCode(r)); return r; };
  return [
    ...materials.map(m => { const types = opt(m.documentTypes as string[] | undefined, []); require(m && code(m.requirementCode) && list(types, 32) && types.every(code)
      && (m.maxAgeDays === null || m.maxAgeDays === undefined || int(m.maxAgeDays)));
      return { kind: 'MATERIAL' as const, code: m.requirementCode as string, subjectRole: role(m), required: bool(m.required, true) === true,
        detail: [...types, ...(int(m.maxAgeDays) ? [`maxAge:${m.maxAgeDays}`] : []), ...(m.waivable === true ? ['waivable'] : [])] }; }),
    ...checks.map(c => { require(c && code(c.checkCode) && nullableCode(opt(c.checkMode as string | null | undefined, null)));
      return { kind: 'CHECK' as const, code: c.checkCode as string, subjectRole: role(c), required: c.blocking === true,
        detail: [...(c.checkMode ? [`mode:${c.checkMode}`] : []), ...(c.blocking === true ? ['blocking'] : []), ...(int(c.validityDays) ? [`validity:${c.validityDays}`] : [])] }; }),
    ...screenings.map(x => { require(x && code(x.screeningType));
      return { kind: 'SCREENING' as const, code: x.screeningType as string, subjectRole: role(x), required: bool(x.required, true) === true,
        detail: x.monitoringRequired === true ? ['monitoring'] : [] }; }),
  ];
}
/** 某辖区可见的程序：只能是该辖区或全局（null）。 */
export function decodePrograms(value: unknown, jurisdictionCode: string): Program[] {
  return items(value, 200).map(raw => {
    const v = raw as Wire;
    const program: Program = { programId: v?.programId as string, programCode: v?.programCode as string, programVersionId: v?.programVersionId as string,
      versionNo: v?.versionNo as number, qualificationType: opt(v?.qualificationType as string | null | undefined, null),
      jurisdictionCode: opt(v?.jurisdictionCode as string | null | undefined, null), tier: opt(v?.tier as string | null | undefined, null),
      ownerPartnerId: opt(v?.ownerPartnerId as string | null | undefined, null), reviewIntervalMonths: opt(v?.reviewIntervalMonths as number | null | undefined, null),
      fourEyesRequired: v?.fourEyesRequired === true, partyType: opt(v?.partyType as string | null | undefined, null), roleType: opt(v?.roleType as string | null | undefined, null),
      programStatus: v?.programStatus as string, versionStatus: v?.versionStatus as string, approvedTime: opt(v?.approvedTime as string | null | undefined, null),
      items: v ? programItems(v) : [] };
    require(v && isId(program.programId) && text(program.programCode, 64) && isId(program.programVersionId) && int(program.versionNo)
      && (program.jurisdictionCode === null || program.jurisdictionCode === jurisdictionCode) && nullableCode(program.qualificationType) && nullableCode(program.tier)
      && (program.ownerPartnerId === null || isId(program.ownerPartnerId)) && (program.reviewIntervalMonths === null || int(program.reviewIntervalMonths))
      && nullableCode(program.partyType) && nullableCode(program.roleType) && code(program.programStatus) && code(program.versionStatus)
      && nullableText(program.approvedTime, 40));
    return program;
  });
}
/** 生效中的版本：程序与版本都 ACTIVE，才会被解析使用。 */
export const programInUse = (p: Program) => p.programStatus === 'ACTIVE' && p.versionStatus === 'ACTIVE';

/** Schema 字段按分区、分组归类，保持字段顺序。 */
export function groupFields(fields: readonly SchemaField[]): { section: string; groups: { group: string; fields: SchemaField[] }[] }[] {
  const sections = new Map<string, Map<string, SchemaField[]>>();
  for (const field of fields) {
    const groups = sections.get(field.section) ?? new Map<string, SchemaField[]>(); sections.set(field.section, groups);
    const group = field.group ?? ''; groups.set(group, [...(groups.get(group) ?? []), field]);
  }
  return [...sections].map(([section, groups]) => ({ section, groups: [...groups].map(([group, list]) => ({ group, fields: list })) }));
}
/** Schema 中引用的目录代码（去重保序），供目录分区快速切换。 */
export const referencedCatalogs = (schemas: readonly OnboardingSchema[]) =>
  [...new Set(schemas.flatMap(s => s.fields.map(f => f.catalogCode)).filter((c): c is string => c !== null))];
