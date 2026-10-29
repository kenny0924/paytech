import { formatCountry, tOr, translator } from '../i18n/index.ts';
import { currencyName, refdataName } from '../referenceData/referenceData.ts';
import type { Translate } from '../i18n/index.ts';

/** 后端 label_key 前缀（client-master DML 原样返回）。 */
export const BACKEND_LABEL_PREFIX = 'onboardingSchema.';

/**
 * 后端 label_key `onboardingSchema.x` 在 `onboarding-schema` 命名空间中为 `onboarding-schema.x`（Platform 命名空间编码只允许小写与连字符）
 * <li>`tSchema` 为 `useT('onboarding-schema')` / `translator('onboarding-schema')`；有译文显示译文，否则显示 fallback。</li>
 */
export function schemaLabel(tSchema: Translate, key: string | null | undefined, fallback: string): string {
  return key?.startsWith(BACKEND_LABEL_PREFIX) ? tOr(tSchema, key.slice(BACKEND_LABEL_PREFIX.length), fallback) : fallback;
}

/** 未登记文案的字段 / 分组名：驼峰或下划线拆词并首字母大写（registrationAuthorityCode → Registration Authority Code）。 */
export function humanize(code: string): string {
  const words = code.replace(/[_.-]+/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2').trim().split(/\s+/);
  return words.map(word => word.length > 1 && word === word.toUpperCase() ? word : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(' ');
}

/** 申请资料的分组 / 字段名：`client-review.application.group|field.*` 有文案用文案，否则拆词显示。 */
export function valueLabel(tReview: Translate, kind: 'group' | 'field', code: string): string {
  return tOr(tReview, `application.${kind}.${code}`, humanize(code));
}

/**
 * 资料值中取自目录的字段 → onboarding-schema 文案键族（与 client-master 采集 Schema 的 catalog_code 及地址 / 标识目录一致）
 * <li>`catalog:<CODE>` 为目录项 `catalog.<CODE>.<值>`；`identifierType` / `organizationType` 为对应目录（组织类型按辖区）。</li>
 * <li>币种与省州是 Platform 参考数据（REF_CURRENCY、REF_COUNTRY_SUBDIVISION）：币种用 Intl，省州用 refdata 文案，取不到时显示编码。</li>
 */
export const FIELD_CATALOGS: Readonly<Record<string, string>> = {
  'organization.organizationType': 'organizationType',
  'organization.registrationAuthorityCode': 'catalog:REGISTRATION_AUTHORITY',
  'business.businessModelCode': 'catalog:BUSINESS_MODEL',
  'business.fulfillmentType': 'catalog:FULFILLMENT_TYPE',
  'business.currency': 'currency',
  'individual.gender': 'catalog:GENDER',
  'individual.sourceOfFundsCode': 'catalog:SOURCE_OF_FUNDS',
  'address.city': 'catalog:CITY',
  'address.stateOrProvince': 'subdivision',
  'identifier.identifierType': 'identifierType',
  'attributes.AE_FREE_ZONE_CODE': 'catalog:FREE_ZONE',
  'attributes.AE_LICENSE_AUTHORITY': 'catalog:REGISTRATION_AUTHORITY',
  'attributes.OCCUPATION': 'catalog:OCCUPATION',
};

/** 取值为 ISO 3166-1 国家代码的资料字段（任一分组）。 */
export const COUNTRY_FIELDS: ReadonlySet<string> = new Set(['countryCode', 'incorporationCountry', 'issuingCountry', 'nationalityCountry',
  'residenceCountry', 'birthCountry', 'TAX_RESIDENCE_COUNTRY']);

/** 资料值显示：布尔值为是 / 否，国家字段显示当前语言的国家名称，目录字段翻译为目录项名称，其他原样；没有译文时显示代码。 */
export function valueText(tSchema: Translate, group: string, field: string, value: string | null, jurisdictionCode: string | null): string {
  if (value === null || value === '') return '—';
  // 布尔资料（如 listedCompany、pepSelfDeclared）以是 / 否文字显示，从不显示 true / false。
  if (value === 'true' || value === 'false') return translator('client-review')(value === 'true' ? 'config.enum.flag.YES' : 'config.enum.flag.NO');
  if (COUNTRY_FIELDS.has(field)) return formatCountry(value);
  const family = FIELD_CATALOGS[`${group}.${field}`];
  if (!family) return value;
  if (family === 'organizationType') return jurisdictionCode ? tOr(tSchema, `organizationType.${jurisdictionCode}.${value}`, value) : value;
  if (family === 'identifierType') return tOr(tSchema, `identifierType.${value}`, value);
  if (family === 'currency') return currencyName(value, null);
  if (family === 'subdivision') return subdivisionText(value);
  return tOr(tSchema, `catalog.${family.slice('catalog:'.length)}.${value}`, value);
}

/** 省州：ISO 3166-2 编码（存量下划线格式 AE_DU 按连字符查找）在 refdata 中的名称，取不到时显示原值。 */
export function subdivisionText(value: string): string {
  const code = /^[A-Z]{2}_[A-Z0-9]{1,3}$/.test(value) ? value.replace('_', '-') : value;
  const name = refdataName(translator('refdata'), 'subdivision', code, null);
  return name === code ? value : name;
}

/** 资料字段名：扩展属性（attributes 组）取属性目录名，其他取审核页字段文案，均无则拆词。 */
export function fieldLabel(tReview: Translate, tSchema: Translate, group: string, field: string): string {
  return group === 'attributes' ? tOr(tSchema, `attribute.${field}`, valueLabel(tReview, 'field', field)) : valueLabel(tReview, 'field', field);
}

/**
 * 业务信息补采（REQ-12）的四个字段：client-master 资料 values 对所有申请都带这些键，旧版 Schema 冻结的申请值为空
 * <li>四项全空表示该申请的表单没有采集它们（"未采集"）；有任一项有值时，空项是申请人未填写的可选 / 批准前补充项（"未填写"）。</li>
 */
export const BUSINESS_INFO_FIELDS: readonly string[] = ['expectedPaymentMethods', 'refundPolicyUrl', 'fulfillmentType', 'fulfillmentDays'];
/** 交付方式为预售时才有天数。 */
const PRE_ORDER = 'PRE_ORDER';
/** 显示为可点击链接（新标签页打开）的网址字段。 */
export const LINK_FIELDS: ReadonlySet<string> = new Set(['websiteUrl', 'refundPolicyUrl']);

/** 资料值的展示形态：文字、空值说明、外部链接、单个目录标签（交付方式）、多值目录标签（逐项"名称 编码"）。 */
export type ValueView =
  | { kind: 'text'; text: string }
  | { kind: 'empty'; text: string }
  | { kind: 'link'; href: string; text: string }
  | { kind: 'tag'; text: string }
  | { kind: 'tags'; tags: { code: string; label: string }[] };

/**
 * 资料值 → 展示形态；返回 null 表示该行不显示（非预售且未填天数）
 * <li>期望支付方式为规范化逗号串（client-master CODE_LIST），逐项取 `paymentMethod.<代码>` 文案，无译文时只显示代码。</li>
 * <li>网址只有 http / https 才成为链接，其他按文字显示。其余字段沿用 valueText。</li>
 *
 * @param fields 同一分组的全部值（判断"未采集"与天数是否适用）
 */
export function valueView(tSchema: Translate, group: string, field: string, value: string | null, jurisdictionCode: string | null,
  fields: Readonly<Record<string, string | null>>): ValueView | null {
  const tReview = translator('client-review');
  const businessInfo = group === 'business' && BUSINESS_INFO_FIELDS.includes(field);
  if (value === null || value === '') {
    if (!businessInfo) return { kind: 'empty', text: '—' };
    if (field === 'fulfillmentDays' && fields.fulfillmentType && fields.fulfillmentType !== PRE_ORDER) return null;
    const collected = BUSINESS_INFO_FIELDS.some(key => fields[key]);
    return { kind: 'empty', text: tReview(collected ? 'application.notFilled' : 'application.notCollected') };
  }
  if (group === 'business' && field === 'expectedPaymentMethods')
    return { kind: 'tags', tags: [...new Set(value.split(',').map(code => code.trim()).filter(Boolean))]
      .map(code => ({ code, label: tOr(tSchema, `paymentMethod.${code}`, code) })) };
  if (group === 'business' && field === 'fulfillmentDays' && /^\d{1,3}$/.test(value))
    return { kind: 'text', text: tReview('application.fulfillmentDaysValue', { count: Number(value) }) };
  if (group === 'business' && field === 'fulfillmentType') return { kind: 'tag', text: valueText(tSchema, group, field, value, jurisdictionCode) };
  if (LINK_FIELDS.has(field) && /^https?:\/\/\S+$/i.test(value)) return { kind: 'link', href: value, text: value };
  return { kind: 'text', text: valueText(tSchema, group, field, value, jurisdictionCode) };
}
