import type { Flat } from "../onboarding.ts";
import type { AddressComponent, FormSchema, IdentifierType, SchemaField, SchemaGroup, SchemaSection } from "./model.ts";
import { REF_COUNTRY_SUBDIVISION } from "./refdata.ts";

/**
 * Schema 驱动表单的纯逻辑：字段 → 草稿路径、可见条件、本地格式校验、任务归属
 * <li>不按辖区、组织类型或标识类型写分支；国家差异只来自 Schema 数据。</li>
 * <li>本地校验只做格式提示；必填、校验位、冲突与关系门槛以服务端核对（CM-9）为准。</li>
 */
export type TaskKey = "profile" | "address" | "people" | "materials" | "declarations" | "review";
export const taskOrder: TaskKey[] = ["profile", "address", "people", "materials", "declarations", "review"];

/** 不进入草稿 PATCH 的控件：人员、材料、声明各有自己的命令。 */
const commandWidgets = new Set(["PERSON_LIST", "DOCUMENT_SLOT", "DECLARATION"]);
export const isDraftField = (field: SchemaField) => !commandWidgets.has(field.widget) && field.requirementLevel !== "HIDDEN";

/** 小写蛇形转小驼峰；小驼峰（`addressLine1`）与大写代码（`SA_VAT_NUMBER`）原样保留。 */
const camel = (name: string) => /^[a-z0-9]+(_[a-z0-9]+)+$/.test(name) ? name.replace(/_([a-z0-9])/g, (_, c: string) => c.toUpperCase()) : name;

/**
 * 证件 / 登记号：每份资料一个主标识（草稿分区 `identifier`）；Schema 中同一 requirementGroup 的多个 IDENTIFIER 字段是"任选其一"，
 * 页面合并为一个控件（类型选择 + 号码）。号码只写不读，读回只有 `identifierMasked`。
 */
export const identifierGroup = (_type?: string) => "identifier";
export const identifierPaths = { type: "identifier.identifierType", value: "identifier.identifierValue", masked: "identifier.identifierMasked",
  issuer: "identifier.issuingCountry", issueDate: "identifier.issueDate", expiryDate: "identifier.expiryDate" } as const;

/**
 * 字段在草稿 values 中的路径（`group.field`）
 * <li>字段键即草稿路径（`organization.legalName`、`attributes.AE_TRN`）；`profile.` 前缀与蛇形段兼容。</li>
 * <li>IDENTIFIER 写入 `identifier.identifierValue`；ADDRESS 一个字段覆盖整组组件，返回空，由地址控件按组件写入。</li>
 */
export function draftPath(field: SchemaField): string | null {
  if (field.widget === "ADDRESS") return null;
  if (field.widget === "IDENTIFIER") return identifierPaths.value;
  const parts = field.fieldKey.split(".");
  const rest = parts[0] === "profile" ? parts.slice(1) : parts;
  if (rest.length !== 2) return null;
  return rest[0] + "." + camel(rest[1]);
}

/** 地址组件 → 草稿字段 `address.<camelCase>`。 */
export const addressPath = (component: AddressComponent) => "address." + camel(component.component);

/**
 * 简单可见条件：`key=value`、`key!=value`、`a|b` 多值，逗号连接表示同时满足
 * <li>引用的 key 是 Schema 字段键，取其草稿路径的当前值；无法解析的条件按可见处理，避免藏住必填项。</li>
 */
export function isVisible(field: SchemaField, values: Flat, schema: FormSchema): boolean {
  if (field.requirementLevel === "HIDDEN") return false;
  const rule = field.visibleWhen?.trim();
  if (!rule) return true;
  return rule.split(",").every((clause) => {
    const match = /^\s*([\w.:-]+)\s*(!=|=)\s*([\w|.:-]*)\s*$/.exec(clause);
    if (!match) return true;
    const [, key, op, expected] = match;
    const target = findField(schema, key);
    const path = target ? draftPath(target) : null;
    const current = path ? values[path] ?? "" : "";
    const hit = expected.split("|").includes(current);
    return op === "=" ? hit : !hit;
  });
}

export function findField(schema: FormSchema, fieldKey: string): SchemaField | undefined {
  for (const section of schema.sections) for (const group of section.groups)
    for (const field of group.fields) if (field.fieldKey === fieldKey) return field;
  return undefined;
}

/** 字段归属的任务：分区决定人员 / 材料 / 声明；地址与联系方式按控件或分组名归入地址任务。 */
export function taskOf(section: SchemaSection, group: SchemaGroup, field: SchemaField): TaskKey {
  const name = section.section.toUpperCase();
  if (name === "PEOPLE" || field.widget === "PERSON_LIST") return "people";
  if (name === "MATERIALS" || field.widget === "DOCUMENT_SLOT") return "materials";
  if (name === "DECLARATIONS" || field.widget === "DECLARATION") return "declarations";
  if (field.widget === "ADDRESS" || /ADDRESS|CONTACT/.test(group.group.toUpperCase())) return "address";
  return "profile";
}

export type FieldBlock = { section: SchemaSection; group: SchemaGroup; fields: SchemaField[] };

/** 某任务内、某主体的草稿字段，按分区 → 分组保持 Schema 顺序；空分组省略。 */
export function taskBlocks(schema: FormSchema, task: TaskKey, subject: "PRIMARY" | "RELATED_PERSON"): FieldBlock[] {
  const blocks: FieldBlock[] = [];
  for (const section of schema.sections) for (const group of section.groups) {
    const fields = group.fields.filter((field) => isDraftField(field)
      && (field.appliesTo === "BOTH" || field.appliesTo === subject)
      && (subject === "RELATED_PERSON" || taskOf(section, group, field) === task));
    if (fields.length) blocks.push({ section, group, fields });
  }
  return blocks;
}

/** 本页面实际展示的任务：没有对应内容的任务（如个人申请的人员）不出现，核对并提交总在最后。 */
export function availableTasks(schema: FormSchema): TaskKey[] {
  const present = new Set<TaskKey>(["review"]);
  for (const section of schema.sections) for (const group of section.groups) for (const field of group.fields)
    if (field.requirementLevel !== "HIDDEN") present.add(taskOf(section, group, field));
  if (schema.materials.some((slot) => !isPersonRole(slot.subjectRole))) present.add("materials");
  if (schema.declarations.length) present.add("declarations");
  return taskOrder.filter((task) => present.has(task));
}

/** 材料槽的主体角色：主申请主体之外的一律视为关系人材料，在人员抽屉里上传。 */
export const isPersonRole = (role: string) => !["CLIENT", "PRIMARY", "SUBJECT", "APPLICANT"].includes(role.toUpperCase());

export type Issue = { key: string; params?: Record<string, string | number> };

/** 当前长度与正则中的固定位数，用来写"应为 10 位数字，当前 9 位"。 */
function fixedDigits(pattern: string): number | null {
  const match = /^\^?(?:\\d|\[0-9\])\{(\d+)\}\$?$/.exec(pattern);
  return match ? Number(match[1]) : null;
}

/** 校验位注册表；未知算法跳过，服务端仍会核对。 */
export const checksums: Record<string, (value: string) => boolean> = {
  NONE: () => true,
  LUHN(value) {
    if (!/^\d+$/.test(value)) return false;
    let sum = 0;
    for (let i = 0; i < value.length; i++) {
      let digit = Number(value[value.length - 1 - i]);
      if (i % 2 === 1) { digit *= 2; if (digit > 9) digit -= 9; }
      sum += digit;
    }
    return sum % 10 === 0;
  },
  /** 埃及国民号只校验结构：世纪位 2/3 + 合法出生日期 + 14 位。 */
  EG_NID(value) {
    const match = /^([23])(\d{2})(\d{2})(\d{2})\d{7}$/.exec(value);
    if (!match) return false;
    const year = (match[1] === "2" ? 1900 : 2000) + Number(match[2]);
    const date = new Date(Date.UTC(year, Number(match[3]) - 1, Number(match[4])));
    return date.getUTCMonth() === Number(match[3]) - 1 && date.getUTCDate() === Number(match[4]) && date.getTime() <= Date.now();
  },
};

/** 规范化证件 / 登记号：去空白与常见分隔符、转大写；与服务端默认规范化一致。 */
export const normalizeIdentifier = (value: string) => value.normalize("NFKC").replace(/[\s\-./]/g, "").toUpperCase();

/**
 * 单字段的本地问题（格式类）；返回 onboarding 命名空间下的提示键
 * <li>空值只在 REQUIRED 且 `requireFilled` 时报缺失（失焦不报，提交前才报）。</li>
 */
export function fieldIssue(field: SchemaField, value: string, requireFilled: boolean, pattern?: string | null,
  checksumRule?: string | null): Issue | null {
  const raw = value.trim();
  if (!raw) return requireFilled && field.requirementLevel === "REQUIRED" ? { key: "error.field_missing" } : null;
  const validation = field.validation ?? {};
  const normalized = field.widget === "IDENTIFIER" ? normalizeIdentifier(raw) : raw;
  const regex = pattern ?? validation.pattern;
  if (validation.minLength && normalized.length < validation.minLength)
    return { key: "error.too_short", params: { min: validation.minLength } };
  if (validation.maxLength && normalized.length > validation.maxLength)
    return { key: "error.too_long", params: { max: validation.maxLength } };
  if (regex) {
    let ok = true;
    try { ok = new RegExp(regex.startsWith("^") ? regex : "^(?:" + regex + ")$").test(normalized); }
    catch { ok = true; warnInvalidPattern(regex); }
    if (!ok) {
      const digits = fixedDigits(regex);
      return digits && /^\d+$/.test(normalized)
        ? { key: "error.digits", params: { expected: digits, actual: normalized.length } }
        : { key: "error.format" };
    }
  }
  const rule = checksumRule ?? validation.checksumRule;
  if (rule && checksums[rule] && !checksums[rule](normalized)) return { key: "error.checksum" };
  if (field.widget === "URL" && !/^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(raw)) return { key: "error.url" };
  if (field.widget === "EMAIL" && !/^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/.test(raw)) return { key: "error.email" };
  if (field.widget === "PHONE" && !/^\+[1-9]\d{6,14}$/.test(raw.replace(/[\s-]/g, ""))) return { key: "error.phone" };
  return null;
}

/** 入驻码：去掉空白与连字符后 8 位字母数字，按 4 位分组显示；不在本地判断有效性。 */
export function formatOnboardingCode(input: string): string {
  const clean = input.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
  return clean.length > 4 ? clean.slice(0, 4) + "-" + clean.slice(4) : clean;
}
export const onboardingCodeComplete = (value: string) => /^[A-Z0-9]{4}-[A-Z0-9]{4}$/.test(value);

/** IDENTIFIER 字段键 `identifier.<TYPE>` 对应的标识类型目录项（正则、校验位、签发国、到期日）。 */
export function identifierType(field: SchemaField, schema: FormSchema): IdentifierType | undefined {
  const code = field.fieldKey.split(".")[1];
  return schema.identifierTypes.find((item) => item.code === code);
}
const allFields = (schema: FormSchema) => schema.sections.flatMap((section) => section.groups.flatMap((group) => group.fields));
/** 同一主体、同一 requirementGroup 的 IDENTIFIER 字段互为备选（如 Emirates ID 或护照）；无分组时只有自己。 */
export function identifierAlternatives(field: SchemaField, schema: FormSchema): SchemaField[] {
  if (field.widget !== "IDENTIFIER" || !field.requirementGroup) return [field];
  return allFields(schema).filter((item) => item.widget === "IDENTIFIER" && item.requirementGroup === field.requirementGroup
    && item.appliesTo === field.appliesTo && item.requirementLevel !== "HIDDEN");
}
/** 一组备选只由第一个字段渲染与校验。 */
export const isIdentifierLead = (field: SchemaField, schema: FormSchema) => identifierAlternatives(field, schema)[0] === field;
/** 当前选中的标识类型：草稿已有且属于备选时用它，否则用第一个备选。 */
export function selectedIdentifier(field: SchemaField, schema: FormSchema, values: Flat): IdentifierType | undefined {
  const alternatives = identifierAlternatives(field, schema);
  const chosen = alternatives.find((item) => item.fieldKey.split(".")[1] === values[identifierPaths.type]) ?? alternatives[0];
  return identifierType(chosen, schema);
}
/** 本地界面状态（如"正在更换证件号"）以 `~` 开头，不进入草稿 PATCH；掩码只读。 */
export const isLocalKey = (key: string) => key.startsWith("~") || key.endsWith(".identifierMasked");

/** 国家包文案的 Platform 命名空间（命名空间码只允许小写字母、数字与连字符）。 */
export const SCHEMA_NAMESPACE = "onboarding-schema";
/** 后端 label_key（`onboardingSchema.*`，来自 client-master DML）→ 本应用多语言键（`onboarding-schema.*`）；其他键原样返回。 */
const reportedPatterns = new Set<string>();
/**
 * 国家包正则在浏览器里编译失败（如 Java 的 (?i)）时本地不校验格式，交给服务端；只警告一次，便于发现配置问题
 * <li>countrypack 测试核对仓库 DML 的全部正则都能在 JavaScript 中编译。</li>
 */
function warnInvalidPattern(pattern: string) {
  if (reportedPatterns.has(pattern)) return;
  reportedPatterns.add(pattern);
  console.warn("Onboarding schema pattern is not valid in JavaScript; format check skipped:", pattern);
}

/**
 * 多证件可选时号码输入框的标签："{类型}号码"；类型名本身已是号码（以"号"、"号码"、"编号"或 Number 结尾）时直接用类型名，避免"登记号号码"
 */
export function identifierNumberLabel(typeLabel: string, template: (type: string) => string): string {
  return /(number|no\.?|\u53f7|\u53f7\u7801|\u7f16\u53f7)\s*$/i.test(typeLabel) ? typeLabel : template(typeLabel);
}

export function schemaI18nKey(labelKey: string): string {
  return labelKey.startsWith("onboardingSchema.") ? SCHEMA_NAMESPACE + labelKey.slice("onboardingSchema".length) : labelKey;
}

export type AddressFormat = NonNullable<FormSchema["addressFormat"]>;
/** 地址国家组件的草稿路径。 */
export const ADDRESS_COUNTRY = "address.countryCode";
/**
 * 当前生效的地址格式：地址国家不是辖区国家时用通用格式（fallbackAddressFormat），否则用辖区格式
 * <li>国家未填时按辖区格式（国家默认取辖区国家）。</li>
 */
export function activeAddressFormat(schema: FormSchema, values: Flat): AddressFormat | null {
  const country = (values[ADDRESS_COUNTRY] ?? "").trim();
  if (country && schema.countryCode && country !== schema.countryCode && schema.fallbackAddressFormat) return schema.fallbackAddressFormat;
  return schema.addressFormat ?? schema.fallbackAddressFormat;
}
/**
 * 国家改变时要清空的组件路径
 * <li>格式切换：新格式中不存在的组件；两边都有的组件保留。</li>
 * <li>已选的行政区（任一格式中取自 REF_COUNTRY_SUBDIVISION 的组件）属于原国家，国家一变就清空。</li>
 */
export function addressPathsToClear(schema: FormSchema, before: Flat, after: Flat): string[] {
  const from = activeAddressFormat(schema, before), to = activeAddressFormat(schema, after);
  if (!from || !to) return [];
  const filled = (path: string) => (before[path] ?? "") !== "";
  const kept = new Set(to.components.map(addressPath));
  const dropped = from.code === to.code ? [] : from.components.map(addressPath).filter((path) => !kept.has(path) && filled(path));
  const countryChanged = (before[ADDRESS_COUNTRY] ?? "") !== (after[ADDRESS_COUNTRY] ?? "");
  const subdivisions = countryChanged ? [...from.components, ...to.components]
    .filter((component) => component.catalogCode === REF_COUNTRY_SUBDIVISION).map(addressPath).filter(filled) : [];
  return [...new Set([...dropped, ...subdivisions])];
}
