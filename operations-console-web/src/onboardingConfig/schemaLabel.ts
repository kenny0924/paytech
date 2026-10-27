import { formatCountry, tOr } from '../i18n/index.ts';
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
 */
export const FIELD_CATALOGS: Readonly<Record<string, string>> = {
  'organization.organizationType': 'organizationType',
  'organization.registrationAuthorityCode': 'catalog:REGISTRATION_AUTHORITY',
  'business.businessModelCode': 'catalog:BUSINESS_MODEL',
  'business.currency': 'catalog:CURRENCY',
  'individual.gender': 'catalog:GENDER',
  'individual.sourceOfFundsCode': 'catalog:SOURCE_OF_FUNDS',
  'address.city': 'catalog:CITY',
  'address.stateOrProvince': 'catalog:STATE_OR_PROVINCE',
  'identifier.identifierType': 'identifierType',
  'attributes.AE_FREE_ZONE_CODE': 'catalog:FREE_ZONE',
  'attributes.OCCUPATION': 'catalog:OCCUPATION',
};

/** 取值为 ISO 3166-1 国家代码的资料字段（任一分组）。 */
export const COUNTRY_FIELDS: ReadonlySet<string> = new Set(['countryCode', 'incorporationCountry', 'issuingCountry', 'nationalityCountry',
  'residenceCountry', 'birthCountry', 'TAX_RESIDENCE_COUNTRY']);

/** 资料值显示：国家字段显示当前语言的国家名称，目录字段翻译为目录项名称，其他原样；没有译文时显示代码。 */
export function valueText(tSchema: Translate, group: string, field: string, value: string | null, jurisdictionCode: string | null): string {
  if (value === null || value === '') return '—';
  if (COUNTRY_FIELDS.has(field)) return formatCountry(value);
  const family = FIELD_CATALOGS[`${group}.${field}`];
  if (!family) return value;
  if (family === 'organizationType') return jurisdictionCode ? tOr(tSchema, `organizationType.${jurisdictionCode}.${value}`, value) : value;
  if (family === 'identifierType') return tOr(tSchema, `identifierType.${value}`, value);
  return tOr(tSchema, `catalog.${family.slice('catalog:'.length)}.${value}`, value);
}

/** 资料字段名：扩展属性（attributes 组）取属性目录名，其他取审核页字段文案，均无则拆词。 */
export function fieldLabel(tReview: Translate, tSchema: Translate, group: string, field: string): string {
  return group === 'attributes' ? tOr(tSchema, `attribute.${field}`, valueLabel(tReview, 'field', field)) : valueLabel(tReview, 'field', field);
}
