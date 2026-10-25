import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { decodeQualifications, entityNoPattern, instant, isAvailability, isId } from '../qualification/qualification.ts';
import type { Availability, Qualification } from '../qualification/qualification.ts';

/**
 * 经营主体与 Local Partner 运营页（client-master 主档 + compliance 资格聚合）的 BFF 路径与页面模型：B1—B6。
 * <li>不新建 OE / Partner；可写的只有 OE 收单资质与 Partner 角色，都以 changeVersion 做并发保护。</li>
 * <li>显示名为 `master-data.enum.<type>.<code>`（entityStatus、sourceType、acquiringQualification、partnerStatus、
 * partnerRole、scopeStatus、disclosureStatus、sourceStatus），用 enumLabel 读取，未知代码原样显示。</li>
 */
export const entityAdminApi = '/entity-admin/v1';
export const masterDataRoutes = {
  entities: '/master-data/operating-entities',
  entity: (entityNo: string) => `/master-data/operating-entities/${entityNo}`,
  partners: '/master-data/local-partners',
  partner: (localPartnerId: string) => `/master-data/local-partners/${localPartnerId}`,
};
export const masterDataButtons = {
  entityManage: 'OPERATIONS_OPERATING_ENTITIES_MANAGE',
  partnerManage: 'OPERATIONS_LOCAL_PARTNERS_MANAGE',
};
export const entityStatuses = ['PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'] as const;
/** OE 来源只有 CLIENT / PARTNER（PLATFORM 已取消）。 */
export const sourceTypes = ['CLIENT', 'PARTNER'] as const;
/** 收单资质三档：MERCHANT 不承接 Client；MERCHANT_RESALE_APPROVED 只承接 MoR；PAYFAC 承接 MoR / PSP。 */
export const acquiringQualifications = ['MERCHANT', 'MERCHANT_RESALE_APPROVED', 'PAYFAC'] as const;
export const partnerStatuses = ['PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED'] as const;
export const partnerRoles = ['OE_PROVIDER', 'REFERRER'] as const;
export const pageSizes = [10, 20, 50, 100];
export { entityNoPattern, isId };

export interface ProviderRef { localPartnerId: string; legalName: string | null }
export interface EntityListItem {
  entityNo: string; legalName: string | null; operatingCountry: string; status: string; sourceType: string | null;
  acquiringQualification: string; provider: ProviderRef | null; changeVersion: number;
}
export interface SourceRelation {
  sourceRelationId: string; sourceType: string; sourceClientId: string | null; sourcePartnerId: string | null; effectiveFrom: string; effectiveTo: string | null;
}
export interface Provider { localPartnerId: string; partnerNo: string; partyId: string; legalName: string | null; status: string; roles: string[] }
export interface Scope {
  scopeId: string; consumerCountryCode: string; currency: string; industryCode: string; mccCode: string; status: string;
  effectiveFrom: string | null; effectiveTo: string | null;
}
export interface SellerDisclosure { disclosureId: string; countryCode: string; locale: string; status: string; effectiveFrom: string; effectiveTo: string | null }
export interface OperatingEntity {
  entityNo: string; partyId: string; legalName: string | null; operatingCountry: string; status: string; acquiringQualification: string;
  changeVersion: number; sourceRelation: SourceRelation | null; provider: Provider | null; scopes: Scope[]; sellerDisclosures: SellerDisclosure[];
}
export interface EntityDetail {
  entity: OperatingEntity; qualificationsStatus: Availability; qualifications: Qualification[];
  providerQualificationsStatus: Availability | 'NOT_APPLICABLE'; providerQualifications: Qualification[];
}
export interface PartnerListItem {
  localPartnerId: string; partnerNo: string; legalName: string | null; status: string; roles: string[]; providedEntityCount: number; changeVersion: number;
}
export interface ProvidedEntity {
  entityNo: string; legalName: string | null; entityStatus: string; sourceRelationId: string; sourceStatus: string; effectiveFrom: string; effectiveTo: string | null;
}
export interface LocalPartner {
  localPartnerId: string; partnerNo: string; partyId: string; legalName: string | null; status: string; roles: string[]; changeVersion: number;
  providedEntities: ProvidedEntity[];
}
export interface PartnerDetail { partner: LocalPartner; qualificationsStatus: Availability; qualifications: Qualification[] }

const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const nullableText = (v: unknown, max = 512) => v === null || text(v, max);
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const country = (v: unknown): v is string => typeof v === 'string' && /^[A-Z]{2}$/.test(v);
const nullableInstant = (v: unknown) => v === null || instant(v);
const nullableId = (v: unknown) => v === null || isId(v);
const version = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 1;
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;
const roles = (v: unknown): v is string[] => list(v, 8) && v.every(code) && new Set(v).size === v.length;
const isEntityNo = (v: unknown): v is string => typeof v === 'string' && entityNoPattern.test(v);
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
function decodePage<T>(value: unknown, decodeItem: (item: unknown) => T, key: (item: T) => string): PagingResponseBody<T> {
  const v = value as PagingResponseBody<unknown>;
  require(v && list(v.items, 100) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1
    && Number.isInteger(v.size) && v.size >= 1 && v.size <= 100);
  const items = v.items.map(decodeItem); require(new Set(items.map(key)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}

function decodeEntityItem(value: unknown): EntityListItem {
  const v = value as EntityListItem;
  require(v && isEntityNo(v.entityNo) && nullableText(v.legalName) && country(v.operatingCountry) && code(v.status) && (v.sourceType === null || code(v.sourceType))
    && code(v.acquiringQualification) && version(v.changeVersion)
    && (v.provider === null || (v.provider && isId(v.provider.localPartnerId) && nullableText(v.provider.legalName))));
  return { entityNo: v.entityNo, legalName: v.legalName, operatingCountry: v.operatingCountry, status: v.status, sourceType: v.sourceType,
    acquiringQualification: v.acquiringQualification, provider: v.provider && { localPartnerId: v.provider.localPartnerId, legalName: v.provider.legalName },
    changeVersion: v.changeVersion };
}
export const decodeEntityPage = (value: unknown) => decodePage(value, decodeEntityItem, item => item.entityNo);

function decodeSource(value: unknown): SourceRelation | null {
  if (value === null) return null;
  const v = value as SourceRelation;
  require(v && isId(v.sourceRelationId) && code(v.sourceType) && nullableId(v.sourceClientId) && nullableId(v.sourcePartnerId)
    && instant(v.effectiveFrom) && nullableInstant(v.effectiveTo));
  return { sourceRelationId: v.sourceRelationId, sourceType: v.sourceType, sourceClientId: v.sourceClientId, sourcePartnerId: v.sourcePartnerId,
    effectiveFrom: v.effectiveFrom, effectiveTo: v.effectiveTo };
}
function decodeProvider(value: unknown): Provider | null {
  if (value === null) return null;
  const v = value as Provider;
  require(v && isId(v.localPartnerId) && text(v.partnerNo, 64) && isId(v.partyId) && nullableText(v.legalName) && code(v.status) && roles(v.roles));
  return { localPartnerId: v.localPartnerId, partnerNo: v.partnerNo, partyId: v.partyId, legalName: v.legalName, status: v.status, roles: [...v.roles] };
}
function decodeScope(value: unknown): Scope {
  const v = value as Scope;
  require(v && isId(v.scopeId) && country(v.consumerCountryCode) && /^[A-Z]{3}$/.test(v.currency) && text(v.industryCode, 64) && /^[0-9]{4}$/.test(v.mccCode)
    && code(v.status) && nullableInstant(v.effectiveFrom) && nullableInstant(v.effectiveTo));
  return { scopeId: v.scopeId, consumerCountryCode: v.consumerCountryCode, currency: v.currency, industryCode: v.industryCode, mccCode: v.mccCode,
    status: v.status, effectiveFrom: v.effectiveFrom, effectiveTo: v.effectiveTo };
}
function decodeDisclosure(value: unknown): SellerDisclosure {
  const v = value as SellerDisclosure;
  require(v && isId(v.disclosureId) && country(v.countryCode) && text(v.locale, 35) && v.locale.length > 0 && code(v.status)
    && instant(v.effectiveFrom) && nullableInstant(v.effectiveTo));
  return { disclosureId: v.disclosureId, countryCode: v.countryCode, locale: v.locale, status: v.status, effectiveFrom: v.effectiveFrom, effectiveTo: v.effectiveTo };
}
/** B2.entity，也是 B3 的结果；必须是请求的经营主体。 */
export function decodeEntity(value: unknown, entityNo: string): OperatingEntity {
  const v = value as OperatingEntity;
  require(v && v.entityNo === entityNo && isId(v.partyId) && nullableText(v.legalName) && country(v.operatingCountry) && code(v.status)
    && code(v.acquiringQualification) && version(v.changeVersion) && list(v.scopes, 1000) && list(v.sellerDisclosures, 1000));
  const sourceRelation = decodeSource(v.sourceRelation ?? null); const provider = decodeProvider(v.provider ?? null);
  return { entityNo: v.entityNo, partyId: v.partyId, legalName: v.legalName, operatingCountry: v.operatingCountry, status: v.status,
    acquiringQualification: v.acquiringQualification, changeVersion: v.changeVersion, sourceRelation, provider,
    scopes: v.scopes.map(decodeScope), sellerDisclosures: v.sellerDisclosures.map(decodeDisclosure) };
}
/** 资格部分标为不可用时列表必须为空，不能当作“未登记”；没有提供方时提供方资格为 NOT_APPLICABLE。 */
export function decodeEntityDetail(value: unknown, entityNo: string): EntityDetail {
  const v = value as EntityDetail;
  require(v && isAvailability(v.qualificationsStatus)
    && (isAvailability(v.providerQualificationsStatus) || v.providerQualificationsStatus === 'NOT_APPLICABLE'));
  const entity = decodeEntity(v.entity, entityNo);
  const qualifications = decodeQualifications(v.qualifications, { roleType: 'OPERATING_ENTITY', roleId: entityNo });
  require(v.qualificationsStatus === 'AVAILABLE' || qualifications.length === 0);
  require((v.providerQualificationsStatus === 'NOT_APPLICABLE') === (entity.provider === null));
  const providerQualifications = entity.provider
    ? decodeQualifications(v.providerQualifications, { roleType: 'LOCAL_PARTNER', roleId: entity.provider.localPartnerId })
    : decodeQualifications(v.providerQualifications);
  require(v.providerQualificationsStatus === 'AVAILABLE' || providerQualifications.length === 0);
  return { entity, qualificationsStatus: v.qualificationsStatus, qualifications, providerQualificationsStatus: v.providerQualificationsStatus, providerQualifications };
}

function decodePartnerItem(value: unknown): PartnerListItem {
  const v = value as PartnerListItem;
  require(v && isId(v.localPartnerId) && text(v.partnerNo, 64) && nullableText(v.legalName) && code(v.status) && roles(v.roles)
    && Number.isSafeInteger(v.providedEntityCount) && v.providedEntityCount >= 0 && version(v.changeVersion));
  return { localPartnerId: v.localPartnerId, partnerNo: v.partnerNo, legalName: v.legalName, status: v.status, roles: [...v.roles],
    providedEntityCount: v.providedEntityCount, changeVersion: v.changeVersion };
}
export const decodePartnerPage = (value: unknown) => decodePage(value, decodePartnerItem, item => item.localPartnerId);

/** B5.partner，也是 B6 的结果；必须是请求的 Partner。 */
export function decodePartner(value: unknown, localPartnerId: string): LocalPartner {
  const v = value as LocalPartner;
  require(v && v.localPartnerId === localPartnerId && text(v.partnerNo, 64) && isId(v.partyId) && nullableText(v.legalName) && code(v.status)
    && roles(v.roles) && version(v.changeVersion) && list(v.providedEntities, 1000));
  const providedEntities = v.providedEntities.map(value => {
    const e = value as ProvidedEntity;
    require(e && isEntityNo(e.entityNo) && nullableText(e.legalName) && code(e.entityStatus) && isId(e.sourceRelationId)
      && (e.sourceStatus === 'ACTIVE' || e.sourceStatus === 'ENDED') && instant(e.effectiveFrom) && nullableInstant(e.effectiveTo));
    return { entityNo: e.entityNo, legalName: e.legalName, entityStatus: e.entityStatus, sourceRelationId: e.sourceRelationId,
      sourceStatus: e.sourceStatus, effectiveFrom: e.effectiveFrom, effectiveTo: e.effectiveTo };
  });
  return { localPartnerId: v.localPartnerId, partnerNo: v.partnerNo, partyId: v.partyId, legalName: v.legalName, status: v.status,
    roles: [...v.roles], changeVersion: v.changeVersion, providedEntities };
}
export function decodePartnerDetail(value: unknown, localPartnerId: string): PartnerDetail {
  const v = value as PartnerDetail;
  require(v && isAvailability(v.qualificationsStatus));
  const partner = decodePartner(v.partner, localPartnerId);
  const qualifications = decodeQualifications(v.qualifications, { roleType: 'LOCAL_PARTNER', roleId: localPartnerId });
  require(v.qualificationsStatus === 'AVAILABLE' || qualifications.length === 0);
  return { partner, qualificationsStatus: v.qualificationsStatus, qualifications };
}

/** 仍有 ACTIVE 提供关系时不能去掉 OE_PROVIDER（client-master 同样返回 422）。 */
export function oeProviderLocked(partner: Pick<LocalPartner, 'roles' | 'providedEntities'>): boolean {
  return partner.roles.includes('OE_PROVIDER') && partner.providedEntities.some(e => e.sourceStatus === 'ACTIVE');
}
/** 角色编辑的提交值：去重并按固定顺序；被锁定的 OE_PROVIDER 始终保留。 */
export function nextRoles(selected: readonly string[], partner: Pick<LocalPartner, 'roles' | 'providedEntities'>): string[] {
  const set = new Set(selected);
  if (oeProviderLocked(partner)) set.add('OE_PROVIDER');
  return partnerRoles.filter(role => set.has(role));
}
export function rolesPayload(selected: readonly string[], partner: LocalPartner, reason: string) {
  return { roles: nextRoles(selected, partner), expectedChangeVersion: partner.changeVersion, reason: reason.trim() };
}
export function acquiringQualificationPayload(entity: OperatingEntity, acquiringQualification: string, reason: string) {
  return { acquiringQualification, expectedChangeVersion: entity.changeVersion, reason: reason.trim() };
}
/** 选择 MERCHANT 时提示：该主体将不再承接 Client 订单。 */
export const merchantWarning = (to: string | undefined) => to === 'MERCHANT';

/** 生效中的卖方披露语言（国家 → locale 列表），用于说明解析按（OE、消费者国家、locale）精确匹配。 */
export function disclosureCountries(disclosures: SellerDisclosure[]): string[] {
  return [...new Set(disclosures.filter(d => d.status === 'ACTIVE').map(d => d.countryCode))].sort();
}
/** 准入范围中的消费者国家里没有生效卖方披露的国家；这些国家的结账会失败。 */
export function countriesMissingDisclosure(entity: Pick<OperatingEntity, 'scopes' | 'sellerDisclosures'>): string[] {
  const covered = new Set(disclosureCountries(entity.sellerDisclosures));
  return [...new Set(entity.scopes.filter(s => s.status === 'ACTIVE').map(s => s.consumerCountryCode))].filter(c => !covered.has(c)).sort();
}

/** 写命令失败的提示 key（master-data 命名空间）；其他错误已由统一通知展示，返回 null。 */
export function commandFailureKey(failure: ApiError | undefined): string | null {
  if (!failure) return null;
  if (failure.status === 409) return failure.code === 'CONCURRENT_REQUEST' ? 'command.busy' : 'command.conflict';
  if (failure.status === 422) return 'command.invalid';
  if (failure.status === 404) return 'command.notFound';
  return null;
}

/** 列表筛选：只发送合法的非空值（BFF 拒绝空参数）。 */
export function entityFilters(values: { status?: string; sourceType?: string; country?: string }): Record<string, string> {
  const result: Record<string, string> = {};
  if (values.status && (entityStatuses as readonly string[]).includes(values.status)) result.status = values.status;
  if (values.sourceType && (sourceTypes as readonly string[]).includes(values.sourceType)) result.sourceType = values.sourceType;
  const c = values.country?.trim().toUpperCase();
  if (c && /^[A-Z]{2}$/.test(c)) result.country = c;
  return result;
}
export function partnerFilters(values: { status?: string; role?: string }): Record<string, string> {
  const result: Record<string, string> = {};
  if (values.status && (partnerStatuses as readonly string[]).includes(values.status)) result.status = values.status;
  if (values.role && (partnerRoles as readonly string[]).includes(values.role)) result.role = values.role;
  return result;
}
