import type { Values } from "../onboarding.ts";
import { safeLogo } from "./refdata.ts";

/**
 * 入驻 v2 浏览器合同（经 client-bff 的 `/onboarding/v1/*` 转发 client-master / compliance）
 * <li>只描述页面消费的字段；未知字段忽略，缺失的可选字段按空处理，不臆造业务事实。</li>
 */
export type ClientType = "INDIVIDUAL" | "ORGANIZATION";
export type RequirementLevel = "REQUIRED" | "OPTIONAL" | "HIDDEN" | "EVENTUALLY";
export type AppliesTo = "PRIMARY" | "RELATED_PERSON" | "BOTH";
export type Widget =
  | "TEXT" | "TEXTAREA" | "DATE" | "COUNTRY" | "SELECT" | "IDENTIFIER" | "ADDRESS" | "NAME" | "PERSON_LIST"
  | "DOCUMENT_SLOT" | "DECLARATION" | "URL" | "MONEY" | "EMAIL" | "PHONE" | "BOOLEAN" | "MULTI_SELECT";

export type Jurisdiction = { jurisdictionCode: string; countryCode: string; labelKey: string; acceptancePolicy: string };
/**
 * 目录项：client-master 自有目录只有代码与文案键；参考数据目录（REF_*，由 BFF 从 Platform 补全）另带英文名、图标与所属国家。
 */
export type CatalogItem = { itemCode: string; labelKey: string; name?: string | null; logoPublicUrl?: string | null; countryCode?: string | null };
/** Platform ACTIVE 国家（BFF 随 Schema 下发）；名称由页面用 Intl 显示，name 是英文回退。 */
export type ReferenceCountry = { code: string; name: string | null; logoPublicUrl: string | null; callingCode: string | null };
export type FieldValidation = { pattern?: string | null; minLength?: number | null; maxLength?: number | null; checksumRule?: string | null };
export type SchemaField = {
  fieldKey: string;
  widget: Widget;
  catalogCode?: string | null;
  requirementLevel: RequirementLevel;
  requirementGroup?: string | null;
  appliesTo: AppliesTo;
  validation?: FieldValidation | null;
  visibleWhen?: string | null;
  /** 二选一组的标题键（如"个人证件"），可空。 */
  requirementGroupLabelKey?: string | null;
  labelKey: string;
  helpKey?: string | null;
  order: number;
};
export type SchemaGroup = { group: string; labelKey: string; fields: SchemaField[] };
export type SchemaSection = { section: string; labelKey: string; groups: SchemaGroup[] };
export type IdentifierType = {
  code: string; partyType: string; labelKey: string; pattern?: string | null; checksumRule?: string | null;
  issuerScopeMode?: string | null; expiryRequired?: boolean | null;
};
export type AddressComponent = { component: string; required: boolean; labelKey: string; order: number; catalogCode?: string | null };
/** 关系类型目录（t_relation_type）：持股是否必填、是否属于受益所有人。 */
export type RelationTypeDef = { code: string; labelKey: string; ruleRole?: string | null; uboFlag?: boolean | null; ownershipPctRequired?: boolean | null };
/** 受益人门槛比较方式：GTE 为"达到门槛"，GT 为"超过门槛"；未下发按 GTE。 */
export type ThresholdComparison = "GTE" | "GT";
export type RelationRules = {
  uboThresholdPct?: number | null; uboFallbackRule?: string | null; controlPersonRequired?: boolean | null;
  minSignatories?: number | null; requiresManager?: boolean | null; requiresDirectors?: boolean | null; uboCollectionMode?: string | null;
  uboThresholdComparison: ThresholdComparison;
};
/** 程序材料槽（CP-1 经 CM-2 合并）；maxAgeDays 按签发日期计算，expiryRequired 表示须填写到期日（未下发视为可选）。 */
export type MaterialSlot = { requirementCode: string; subjectRole: string; documentTypes: string[]; required: boolean; maxAgeDays?: number | null;
  expiryRequired?: boolean | null };
/** 声明定义；documentVersion 是需要阅读的条款 / 告知当前版本，确认时须原样带回（无文档为空）。 */
export type DeclarationDef = { declarationType: string; labelKey: string; documentVersion?: string | null };
export type OrganizationType = { code: string; labelKey: string };
export type FormSchema = {
  schemaId: string;
  versionNo: number;
  sections: SchemaSection[];
  catalogs: Record<string, CatalogItem[]>;
  /** 可选国家与地区；为空表示选项未能加载（页面显示重试，不使用内置列表）。 */
  countries: ReferenceCountry[];
  organizationTypes: OrganizationType[];
  identifierTypes: IdentifierType[];
  addressFormat: { code: string; components: AddressComponent[] } | null;
  /** 地址国家不是辖区国家时使用的通用格式（GENERIC）。 */
  fallbackAddressFormat: { code: string; components: AddressComponent[] } | null;
  /** 辖区所在国家（ISO 3166-1），用于判断地址是否为本国地址；未下发时为空。 */
  countryCode: string | null;
  relationRules: RelationRules;
  relationTypes: RelationTypeDef[];
  materials: MaterialSlot[];
  declarations: DeclarationDef[];
};

export type FileInfo = {
  fileId: string; fileAddress: string; displayName: string; sizeBytes: string; mimeType: string;
  scanStatus: string; objectStatus: string; allowedActions: string[];
};
export type Relation = {
  relationId: string; relatedPartyId: string; relationType: string; ownershipPercentage: string | null;
  effectiveFrom: string | null; effectiveTo: string | null; status: string; editable: boolean;
  uboBasis?: string | null; jobTitle?: string | null; nomineeFlag?: boolean | null; controlNature?: string | null;
};
export type Person = { partyId: string; editable: boolean; values: Values };
export type DocumentRef = {
  documentId: string; subjectPartyId: string; fileId: string; fileAddress: string; requirementCode: string;
  documentType: string; subjectInScope: boolean; fileStatus?: string; file: FileInfo | null; issueDate?: string | null; expiryDate?: string | null;
};
/**
 * 声明记录（每类型最新一条）
 * <li>current 为 false 表示确认后资料或条款版本已变化，需要重新确认；旧服务未下发时按有效处理。</li>
 */
export type DeclarationRecord = {
  declarationType: string; declaredValue: string; declaredAt?: string | null; declaredBy?: string | null;
  termsVersion?: string | null; draftRevision?: string | number | null; current?: boolean | null;
};

/** 需求清单一项（CM-7，实时计算）；带期限的项来自案件退回。 */
export type RequirementItem = {
  requirementKind: string; requirementKey: string; subjectPartyId: string | null; status: string;
  awaitingActionFrom: string | null; errorCode: string | null; remediationCode: string | null; dueTime: string | null;
};
/** 需求级错误（CM-9 核对 / 422 与 CP-4 退回共用形状）；message / reason 是英文过渡字段，页面只用 code。 */
export type RequirementError = {
  requirementKey: string; subjectPartyId: string | null; code: string; remediation: string | null;
  message?: string | null; reason?: string | null; dueTime?: string | null;
};
/** CP-4 客户可见审核结果。 */
export type ReviewResult = {
  caseStatus: string | null; outcome: string | null; publicNote: string | null; decidedAt: string | null;
  dueTime: string | null; errors: RequirementError[];
};
export type Application = {
  applicationId: string; applicationNo: string; clientId?: string | null; clientType: ClientType; subjectPartyId: string;
  status: string; savedAt: string | null; editable: boolean;
  jurisdictionCode: string | null; organizationTypeCode: string | null; schemaId?: string | null;
  /** 退回补件截止时间（client-master 冻结自案件），与审核结果的 dueTime 一致。 */
  reviewDueTime?: string | null;
  responsiblePartnerName?: string | null; submittedAt?: string | null; withdrawnAt?: string | null;
  values: Values; people: Person[]; relations: Relation[]; documents: DocumentRef[]; declarations: DeclarationRecord[];
  reviewStatus?: "NONE" | "AVAILABLE" | "UNAVAILABLE"; review?: ReviewResult | null;
  requirementsStatus?: "AVAILABLE" | "UNAVAILABLE"; requirements?: RequirementItem[];
};
export type StartSummary = {
  workspaces?: unknown;
  applications: { applicationId: string; applicationNo: string; clientType: string; status: string; active: boolean }[];
  activeApplicationId: string | null; hasMore: boolean;
};

export const api = {
  start: "/onboarding/v1/start",
  jurisdictions: "/onboarding/v1/jurisdictions",
  /** 有申请时带 applicationId：Owner 返回申请冻结的 Schema 版本与责任方。 */
  schema: (jurisdiction: string, partyType: ClientType, organizationType: string | null, applicationId?: string) =>
    "/onboarding/v1/form-schema?" + new URLSearchParams({
      jurisdiction, partyType, ...(organizationType ? { organizationType } : {}), ...(applicationId ? { applicationId } : {}),
    }).toString(),
  applications: "/onboarding/v1/applications",
  application: (id: string) => "/onboarding/v1/applications/" + id,
  draft: (id: string) => "/onboarding/v1/applications/" + id + "/draft",
  people: (id: string) => "/onboarding/v1/applications/" + id + "/people",
  person: (id: string, party: string) => "/onboarding/v1/applications/" + id + "/people/" + party,
  relations: (id: string) => "/onboarding/v1/applications/" + id + "/relations",
  relation: (id: string, relation: string) => "/onboarding/v1/applications/" + id + "/relations/" + relation,
  documents: (id: string) => "/onboarding/v1/applications/" + id + "/documents",
  document: (id: string, document: string) => "/onboarding/v1/applications/" + id + "/documents/" + document,
  declarations: (id: string) => "/onboarding/v1/applications/" + id + "/declarations",
  requirements: (id: string) => "/onboarding/v1/applications/" + id + "/requirements",
  withdrawal: (id: string) => "/onboarding/v1/applications/" + id + "/withdrawal",
  submissions: (id: string) => "/onboarding/v1/applications/" + id + "/submissions",
  submissionCheck: (id: string) => "/onboarding/v1/applications/" + id + "/submission-check",
};

const text = (value: unknown): string | null => (typeof value === "string" && value.length > 0 ? value : null);
const list = <T>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);
const code = /^[A-Za-z0-9_.:-]{1,128}$/;

/** 辖区列表；服务端可能直接返回数组或包在 items 里。只保留结构完整的项。 */
export function readJurisdictions(value: unknown): Jurisdiction[] {
  const raw = Array.isArray(value) ? value : list((value as { items?: unknown; jurisdictions?: unknown })?.items
    ?? (value as { jurisdictions?: unknown })?.jurisdictions);
  return (raw as Jurisdiction[]).filter((item) => item && code.test(item.jurisdictionCode ?? "") && typeof item.labelKey === "string")
    .filter((item) => item.acceptancePolicy !== "CLOSED");
}

function readField(value: SchemaField): SchemaField | null {
  if (!value || typeof value.fieldKey !== "string" || typeof value.widget !== "string") return null;
  return {
    ...value,
    requirementLevel: (value.requirementLevel ?? "OPTIONAL") as RequirementLevel,
    appliesTo: (value.appliesTo ?? "PRIMARY") as AppliesTo,
    labelKey: value.labelKey ?? "",
    order: Number.isFinite(value.order) ? value.order : 0,
  };
}

/**
 * 校验并规整采集 Schema（CM-2）
 * <li>结构不完整（无 schemaId 或 sections）时抛错，页面显示"表单暂不可用"而不是渲染半张表。</li>
 * <li>分组与字段按 order 排序；HIDDEN 字段保留在模型里，由渲染器跳过。</li>
 */
export function readSchema(value: unknown, unavailable: string): FormSchema {
  const raw = value as Partial<FormSchema> & { schemaId?: unknown; versionNo?: unknown };
  if (!raw || (typeof raw.schemaId !== "string" && typeof raw.schemaId !== "number") || !Array.isArray(raw.sections))
    throw new Error(unavailable);
  const sections = raw.sections.map((section) => ({
    section: String(section.section),
    labelKey: section.labelKey ?? "",
    groups: list<SchemaGroup>(section.groups).map((group) => ({
      group: String(group.group),
      labelKey: group.labelKey ?? "",
      fields: list<SchemaField>(group.fields).map(readField).filter((field): field is SchemaField => !!field)
        .sort((a, b) => a.order - b.order),
    })),
  }));
  const organizationTypes = list<OrganizationType & { typeCode?: string; itemCode?: string }>(raw.organizationTypes)
    .map((item) => ({ code: item.code ?? item.typeCode ?? item.itemCode ?? "", labelKey: item.labelKey ?? "" }))
    .filter((item) => item.code);
  const sortFormat = (value: unknown): FormSchema["addressFormat"] => {
    const format = value as FormSchema["addressFormat"];
    return format && Array.isArray(format.components) ? { code: format.code, components: [...format.components].sort((a, b) => a.order - b.order) } : null;
  };
  const fallback = sortFormat((raw as { fallbackAddressFormat?: unknown }).fallbackAddressFormat);
  const declarationFields = sections.flatMap((section) => section.groups.flatMap((group) => group.fields))
    .filter((field) => field.widget === "DECLARATION" && field.requirementLevel !== "HIDDEN")
    .map((field) => ({ declarationType: field.fieldKey.split(".").slice(1).join("."), labelKey: field.labelKey }));
  const declarationDefs = list<DeclarationDef>(raw.declarations).filter((item) => item && typeof item.declarationType === "string")
    .map((item) => ({ ...item, documentVersion: text(item.documentVersion) }));
  const relationTypes = list<RelationTypeDef & { relationTypeCode?: string; itemCode?: string }>((raw as { relationTypes?: unknown }).relationTypes)
    .map((item) => ({ ...item, code: item.code ?? item.relationTypeCode ?? item.itemCode ?? "" })).filter((item) => item.code);
  // 目录项 Owner 用 code，旧形状用 itemCode，统一为 itemCode；参考数据目录另保留英文名、图标与所属国家。
  const catalogs = Object.fromEntries(Object.entries(raw.catalogs && typeof raw.catalogs === "object" ? raw.catalogs : {})
    .map(([key, items]) => [key, list<CatalogItem & { code?: string }>(items).filter((item) => item && typeof item === "object")
      .map((item) => ({ itemCode: item.itemCode ?? item.code ?? "", labelKey: item.labelKey ?? "", name: text(item.name),
        logoPublicUrl: safeLogo(item.logoPublicUrl), countryCode: text(item.countryCode) }))
      .filter((item) => item.itemCode)])) as Record<string, CatalogItem[]>;
  const countries = list<ReferenceCountry>((raw as { countries?: unknown }).countries)
    .filter((item) => item && typeof item.code === "string" && /^[A-Z]{2}$/.test(item.code))
    .map((item) => ({ code: item.code, name: text(item.name), logoPublicUrl: safeLogo(item.logoPublicUrl), callingCode: text(item.callingCode) }));
  return {
    schemaId: String(raw.schemaId),
    versionNo: Number(raw.versionNo ?? 1),
    sections,
    catalogs,
    countries,
    organizationTypes,
    identifierTypes: list<IdentifierType>(raw.identifierTypes).filter((item) => item && typeof item.code === "string"),
    addressFormat: sortFormat(raw.addressFormat) ?? fallback,
    fallbackAddressFormat: fallback,
    countryCode: text((raw as { countryCode?: unknown }).countryCode) ?? text((raw as { jurisdictionCode?: unknown }).jurisdictionCode),
    relationRules: relationRules(raw.relationRules),
    relationTypes: relationTypes.length ? relationTypes
      : (catalogs.RELATION_TYPE ?? []).map((item) => ({ code: item.itemCode, labelKey: item.labelKey })),
    materials: list<MaterialSlot>(raw.materials).filter((item) => item && typeof item.requirementCode === "string")
      .map((item) => ({ ...item, documentTypes: list<string>(item.documentTypes) })),
    declarations: declarationDefs.length ? declarationDefs : declarationFields,
  };
}

/** 规则数值可能以字符串下发（如 "25"）；无法解析的值视为未设置。 */
function relationRules(value: unknown): RelationRules {
  const raw = (value ?? {}) as Record<string, unknown>;
  const number = (item: unknown) => item === null || item === undefined || item === "" ? null : Number.isFinite(Number(item)) ? Number(item) : null;
  return { ...(raw as RelationRules), uboThresholdPct: number(raw.uboThresholdPct), minSignatories: number(raw.minSignatories),
    uboThresholdComparison: raw.uboThresholdComparison === "GT" ? "GT" : "GTE" };
}

function readErrors(value: unknown): RequirementError[] {
  return list<RequirementError>(value).filter((item) => item && typeof item.requirementKey === "string" && typeof item.code === "string")
    .map((item) => ({ ...item, subjectPartyId: text(item.subjectPartyId), remediation: text(item.remediation) }));
}

/** 申请读出（BFF 聚合）；旧字段缺失时补空，审核结果与需求清单各自可降级。 */
export function readApplication(value: unknown): Application {
  const raw = value as Application;
  const review = raw.review
    ? { caseStatus: text(raw.review.caseStatus), outcome: text(raw.review.outcome), publicNote: text(raw.review.publicNote),
      decidedAt: text(raw.review.decidedAt), dueTime: text(raw.review.dueTime), errors: readErrors(raw.review.errors) }
    : null;
  return {
    ...raw,
    jurisdictionCode: text(raw.jurisdictionCode),
    organizationTypeCode: text(raw.organizationTypeCode),
    values: raw.values ?? {},
    people: list(raw.people),
    relations: list(raw.relations),
    documents: list(raw.documents),
    declarations: list(raw.declarations),
    review,
    requirements: list<RequirementItem>(raw.requirements),
  };
}

/** 需求清单（CM-7）。 */
export function readRequirements(value: unknown): RequirementItem[] {
  const raw = Array.isArray(value) ? value : (value as { items?: unknown })?.items;
  return list<RequirementItem>(raw).filter((item) => item && typeof item.requirementKey === "string");
}

/** 提交前核对：`{ready, errors[]}`；结构异常不能被当作"可以提交"。 */
export function readCheck(value: unknown, unavailable: string): { ready: boolean; errors: RequirementError[] } {
  const raw = value as { ready?: unknown; errors?: unknown };
  if (!raw || typeof raw.ready !== "boolean" || !Array.isArray(raw.errors)) throw new Error(unavailable);
  const errors = readErrors(raw.errors);
  if (raw.ready !== (errors.length === 0)) throw new Error(unavailable);
  return { ready: raw.ready, errors };
}

/**
 * 从 Problem（422）体取需求级错误
 * <li>需求级形状 `{requirementKey, code, remediation}` 直接读取。</li>
 * <li>正式提交的 Problem 形状 `{field, message}`：field 为需求键（关系人为 `people[<partyId>].<key>`），message 为 `错误码/补救码`。</li>
 */
export function problemErrors(body: unknown): RequirementError[] {
  const raw = list<{ field?: unknown; message?: unknown }>((body as { errors?: unknown })?.errors);
  const legacy: RequirementError[] = [];
  for (const item of raw) {
    if (typeof item?.field !== "string" || typeof item.message !== "string") continue;
    const codes = /^([A-Z][A-Z0-9_]{0,63})(?:\/([A-Z][A-Z0-9_]{0,63}))?$/.exec(item.message);
    if (!codes) continue;
    const person = /^people\[([1-9][0-9]{0,18})\]\.(.+)$/.exec(item.field);
    legacy.push({ requirementKey: person ? person[2] : item.field, subjectPartyId: person ? person[1] : null,
      code: codes[1], remediation: codes[2] ?? null });
  }
  return [...readErrors(raw), ...legacy];
}
