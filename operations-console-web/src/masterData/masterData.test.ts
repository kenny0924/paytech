import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import { enumLabel } from '../i18n/index.ts';
import { testT } from '../i18n/testing.ts';
import { hasButton } from '../qualification/qualification.ts';
import {
  acquiringQualificationPayload, acquiringQualifications, commandFailureKey, countriesMissingDisclosure, decodeEntity, decodeEntityDetail, decodeEntityPage,
  decodePartner, decodePartnerDetail, decodePartnerPage, entityFilters, entityStatuses, masterDataButtons, masterDataRoutes, merchantWarning, nextRoles,
  oeProviderLocked, partnerFilters, partnerRoles, partnerStatuses, rolesPayload, sourceTypes,
} from './masterData.ts';

const t = testT('master-data');

const provider = { localPartnerId: '9001', partnerNo: 'LP9001', partyId: '880000000002', legalName: 'Our PayFac', status: 'ACTIVE', roles: ['OE_PROVIDER'] };
const partnerEntity = {
  entityNo: 'AE001', partyId: '880000000001', legalName: 'PayFac AE LLC', operatingCountry: 'AE', status: 'ACTIVE', acquiringQualification: 'PAYFAC', changeVersion: 3,
  sourceRelation: { sourceRelationId: '5001', sourceType: 'PARTNER', sourceClientId: null, sourcePartnerId: '9001', effectiveFrom: '2026-10-24T00:00:00.000000Z', effectiveTo: null },
  provider,
  scopes: [{ scopeId: '5101', consumerCountryCode: 'AE', currency: 'USD', industryCode: 'RETAIL', mccCode: '5411', status: 'ACTIVE', effectiveFrom: '2026-10-24T00:00:00.000000Z', effectiveTo: null },
    { scopeId: '5102', consumerCountryCode: 'SA', currency: 'USD', industryCode: 'RETAIL', mccCode: '5411', status: 'ACTIVE', effectiveFrom: null, effectiveTo: null }],
  sellerDisclosures: [{ disclosureId: '5201', countryCode: 'AE', locale: 'en', status: 'ACTIVE', effectiveFrom: '2026-10-24T00:00:00.000000Z', effectiveTo: null }],
};
const clientEntity = { ...partnerEntity, provider: null,
  sourceRelation: { ...partnerEntity.sourceRelation, sourceType: 'CLIENT', sourceClientId: '8101', sourcePartnerId: null } };
const q = (qualificationId: string, roleType: string, roleId: string, qualificationType: string) => ({ qualificationId, roleType, roleId, qualificationType,
  status: 'APPROVED', effectiveStatus: 'ELIGIBLE', validFrom: '2026-10-24T00:00:00.000000Z', validTo: null, currentDecisionId: '6101', decisionSource: 'MANUAL',
  reviewStatus: 'NOT_REQUIRED', version: '1', updatedAt: '2026-10-24T00:00:00.000000Z' });
const oeQ = q('6001', 'OPERATING_ENTITY', 'AE001', 'ENTITY_UNDERWRITING');
const partnerQ = q('6002', 'LOCAL_PARTNER', '9001', 'PARTNER_DUE_DILIGENCE');
const partner = { localPartnerId: '9001', partnerNo: 'LP9001', partyId: '880000000002', legalName: 'Our PayFac', status: 'ACTIVE', roles: ['OE_PROVIDER'], changeVersion: 2,
  providedEntities: [{ entityNo: 'AE001', legalName: 'PayFac AE LLC', entityStatus: 'ACTIVE', sourceRelationId: '5001', sourceStatus: 'ACTIVE',
    effectiveFrom: '2026-10-24T00:00:00.000000Z', effectiveTo: null }] };
const button = (resourceCode: string): ConsoleResource => ({ resourceId: '1', parentResourceId: '2', resourceType: 'BUTTON', resourceCode,
  displayName: resourceCode, componentKey: null, frontendRoute: null, sortOrder: 10 } as unknown as ConsoleResource);

test('entity list and detail decoders keep owner facts; numeric change versions; provider only with a provider', () => {
  const page = decodeEntityPage({ items: [{ entityNo: 'AE001', legalName: 'PayFac AE LLC', operatingCountry: 'AE', status: 'ACTIVE', sourceType: 'PARTNER',
    acquiringQualification: 'PAYFAC', provider: { localPartnerId: '9001', legalName: 'Our PayFac' }, changeVersion: 3 }], page: 1, size: 20, total: 1 });
  assert.equal(page.items[0].provider?.localPartnerId, '9001');
  const detail = decodeEntityDetail({ entity: partnerEntity, qualificationsStatus: 'AVAILABLE', qualifications: [oeQ],
    providerQualificationsStatus: 'AVAILABLE', providerQualifications: [partnerQ] }, 'AE001');
  assert.equal(detail.entity.scopes[0].mccCode, '5411');
  assert.equal(detail.providerQualifications[0].roleId, '9001');
  assert.throws(() => decodeEntity(partnerEntity, 'AE002'));
  assert.throws(() => decodeEntity({ ...partnerEntity, changeVersion: '3' }, 'AE001'));
  assert.throws(() => decodeEntityPage({ items: [], page: 1, size: 200, total: 0 }));
});

test('entity detail availability: UNAVAILABLE parts must be empty; NOT_APPLICABLE exactly when there is no provider', () => {
  const degraded = decodeEntityDetail({ entity: partnerEntity, qualificationsStatus: 'UNAVAILABLE', qualifications: [],
    providerQualificationsStatus: 'UNAVAILABLE', providerQualifications: [] }, 'AE001');
  assert.equal(degraded.qualificationsStatus, 'UNAVAILABLE');
  assert.equal(degraded.providerQualificationsStatus, 'UNAVAILABLE');
  assert.throws(() => decodeEntityDetail({ entity: partnerEntity, qualificationsStatus: 'UNAVAILABLE', qualifications: [oeQ],
    providerQualificationsStatus: 'AVAILABLE', providerQualifications: [] }, 'AE001'));
  assert.equal(decodeEntityDetail({ entity: clientEntity, qualificationsStatus: 'AVAILABLE', qualifications: [],
    providerQualificationsStatus: 'NOT_APPLICABLE', providerQualifications: [] }, 'AE001').entity.provider, null);
  assert.throws(() => decodeEntityDetail({ entity: clientEntity, qualificationsStatus: 'AVAILABLE', qualifications: [],
    providerQualificationsStatus: 'AVAILABLE', providerQualifications: [] }, 'AE001'));
  assert.throws(() => decodeEntityDetail({ entity: partnerEntity, qualificationsStatus: 'AVAILABLE', qualifications: [partnerQ],
    providerQualificationsStatus: 'AVAILABLE', providerQualifications: [] }, 'AE001'));
});

test('partner decoders: requested partner only, qualifications belong to it', () => {
  assert.equal(decodePartnerPage({ items: [{ localPartnerId: '9001', partnerNo: 'LP9001', legalName: null, status: 'ACTIVE', roles: ['OE_PROVIDER'],
    providedEntityCount: 1, changeVersion: 2 }], page: 1, size: 10, total: 1 }).items[0].providedEntityCount, 1);
  const detail = decodePartnerDetail({ partner, qualificationsStatus: 'AVAILABLE', qualifications: [partnerQ] }, '9001');
  assert.equal(detail.partner.providedEntities[0].sourceStatus, 'ACTIVE');
  assert.throws(() => decodePartner(partner, '9002'));
  assert.throws(() => decodePartner({ ...partner, roles: ['OE_PROVIDER', 'OE_PROVIDER'] }, '9001'));
  assert.throws(() => decodePartnerDetail({ partner, qualificationsStatus: 'AVAILABLE', qualifications: [oeQ] }, '9001'));
  assert.equal(decodePartnerDetail({ partner, qualificationsStatus: 'UNAVAILABLE', qualifications: [] }, '9001').qualificationsStatus, 'UNAVAILABLE');
});

test('OE_PROVIDER cannot be removed while the partner still provides an active entity', () => {
  const live = decodePartner(partner, '9001');
  assert.equal(oeProviderLocked(live), true);
  assert.deepEqual(nextRoles(['REFERRER'], live), ['OE_PROVIDER', 'REFERRER']);
  assert.deepEqual(rolesPayload([], live, ' Referral programme '), { roles: ['OE_PROVIDER'], expectedChangeVersion: 2, reason: 'Referral programme' });
  const ended = decodePartner({ ...partner, providedEntities: [{ ...partner.providedEntities[0], sourceStatus: 'ENDED', effectiveTo: '2026-10-25T00:00:00.000000Z' }] }, '9001');
  assert.equal(oeProviderLocked(ended), false);
  assert.deepEqual(nextRoles(['REFERRER'], ended), ['REFERRER']);
  assert.deepEqual(nextRoles(['REFERRER', 'OE_PROVIDER', 'REFERRER'], ended), ['OE_PROVIDER', 'REFERRER']);
  assert.equal(oeProviderLocked(decodePartner({ ...partner, roles: ['REFERRER'] }, '9001')), false);
});

test('acquiring qualification change: expected version from the entity, MERCHANT warns, three tiers described', () => {
  const entity = decodeEntity(partnerEntity, 'AE001');
  assert.deepEqual(acquiringQualificationPayload(entity, 'MERCHANT', ' Downgrade '), { acquiringQualification: 'MERCHANT', expectedChangeVersion: 3, reason: 'Downgrade' });
  assert.equal(merchantWarning('MERCHANT'), true);
  assert.equal(merchantWarning('PAYFAC'), false);
  for (const tier of acquiringQualifications) {
    assert.ok(t.has(`acquiring.description.${tier}`), tier);
    assert.ok(t.has(`enum.acquiringQualification.${tier}`), tier);
  }
  for (const role of partnerRoles) assert.ok(t.has(`roles.description.${role}`), role);
});

test('write buttons follow the MANAGE resources of block 137', () => {
  assert.equal(hasButton([button(masterDataButtons.entityManage)], masterDataButtons.entityManage), true);
  assert.equal(hasButton([button(masterDataButtons.entityManage)], masterDataButtons.partnerManage), false);
  assert.equal(hasButton([], masterDataButtons.entityManage), false);
  assert.equal(masterDataButtons.entityManage, 'OPERATIONS_OPERATING_ENTITIES_MANAGE');
  assert.equal(masterDataButtons.partnerManage, 'OPERATIONS_LOCAL_PARTNERS_MANAGE');
});

test('seller disclosures: countries in active scopes without an active disclosure are reported', () => {
  assert.deepEqual(countriesMissingDisclosure(decodeEntity(partnerEntity, 'AE001')), ['SA']);
});

test('filters send only valid non-empty values; PLATFORM is not a source', () => {
  assert.deepEqual(entityFilters({ status: 'ACTIVE', sourceType: 'PARTNER', country: ' ae ' }), { status: 'ACTIVE', sourceType: 'PARTNER', country: 'AE' });
  assert.deepEqual(entityFilters({ status: '', sourceType: 'PLATFORM', country: 'UAE' }), {});
  assert.deepEqual([...sourceTypes], ['CLIENT', 'PARTNER']);
  assert.deepEqual(partnerFilters({ status: 'TERMINATED', role: 'REFERRER' }), { status: 'TERMINATED', role: 'REFERRER' });
  assert.deepEqual(partnerFilters({ role: 'PROVIDER' }), {});
});

test('command failures map to refresh / busy / invalid / not found text', () => {
  assert.equal(commandFailureKey(new ApiError(409, 'ENTITY_CHANGE_VERSION_CONFLICT')), 'command.conflict');
  assert.equal(commandFailureKey(new ApiError(409, 'CONCURRENT_REQUEST')), 'command.busy');
  assert.equal(commandFailureKey(new ApiError(422, 'ENTITY_ATTRIBUTE_INVALID')), 'command.invalid');
  assert.equal(commandFailureKey(new ApiError(404, 'OPERATING_ENTITY_NOT_FOUND')), 'command.notFound');
  assert.equal(commandFailureKey(new ApiError(500, 'INTERNAL_ERROR')), null);
  for (const key of ['command.conflict', 'command.busy', 'command.invalid', 'command.notFound']) assert.ok(t.has(key), key);
});

test('detail routes: entityNo and localPartnerId under their menus', () => {
  const menu = (componentKey: string, frontendRoute: string) => ({ resourceId: '1', parentResourceId: null, resourceType: 'MENU', resourceCode: componentKey,
    displayName: '', componentKey, frontendRoute, sortOrder: 60 } as unknown as ConsoleResource);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_OPERATING_ENTITIES', masterDataRoutes.entities)), [masterDataRoutes.entities, `${masterDataRoutes.entities}/:entityNo`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_LOCAL_PARTNERS', masterDataRoutes.partners)), [masterDataRoutes.partners, `${masterDataRoutes.partners}/:localPartnerId`]);
  assert.equal(masterDataRoutes.entity('AE001'), '/master-data/operating-entities/AE001');
  assert.equal(masterDataRoutes.partner('9001'), '/master-data/local-partners/9001');
});

test('labels: every known status and role has an English name; unknown codes stay raw', () => {
  const groups: [string, readonly string[]][] = [['enum.entityStatus', entityStatuses], ['enum.sourceType', sourceTypes], ['enum.partnerStatus', partnerStatuses],
    ['enum.partnerRole', partnerRoles], ['enum.sourceStatus', ['ACTIVE', 'ENDED']]];
  for (const [prefix, codes] of groups) for (const code of codes) assert.ok(t.has(`${prefix}.${code}`), `${prefix}.${code}`);
  assert.equal(enumLabel(t, 'enum.partnerRole', 'OE_PROVIDER'), 'OE Provider');
  assert.equal(enumLabel(t, 'enum.partnerRole', 'SPLIT_PAYEE'), 'SPLIT_PAYEE');
});
