import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { enumLabel } from '../i18n/index.ts';
import { testT } from '../i18n/testing.ts';
import {
  allowedQualificationTypes, commandFailureKey, decisionSources, decodeHistory, decodeQualification, decodeQualificationPage, decodeQualifications,
  decodeSubject, effectiveStatuses, hasButton, manualDecisionPayload, qualificationActions, qualificationLink, qualificationManageButton,
  qualificationRoute, qualificationStatuses, qualificationTypes, readListQuery, revocationPayload, roleTypes, subjectQualificationSlots, triggerTypes, utcMicros,
} from './qualification.ts';

const t = testT('qualification');

const oeQualification = {
  qualificationId: '6001', roleType: 'OPERATING_ENTITY', roleId: 'AE001', qualificationType: 'ENTITY_UNDERWRITING', status: 'APPROVED',
  effectiveStatus: 'ELIGIBLE', validFrom: '2026-10-24T00:00:00.000000Z', validTo: null, currentDecisionId: '6101', decisionSource: 'MANUAL',
  reviewStatus: 'NOT_REQUIRED', version: '2', updatedAt: '2026-10-24T00:00:00.000000Z',
};
const clientQualification = { ...oeQualification, qualificationId: '6003', roleType: 'CLIENT', roleId: '8101', qualificationType: 'ORGANIZATION_KYB',
  decisionSource: 'REVIEWER' };
const button = (resourceCode: string): ConsoleResource => ({ resourceId: '1', parentResourceId: '2', resourceType: 'BUTTON', resourceCode,
  displayName: resourceCode, componentKey: null, frontendRoute: null, sortOrder: 10 } as unknown as ConsoleResource);

test('qualification decoder: roleId follows the role (entityNo for OE, decimal ID otherwise), versions stay decimal strings', () => {
  assert.equal(decodeQualification(oeQualification).roleId, 'AE001');
  assert.equal(decodeQualification(clientQualification).version, '2');
  assert.throws(() => decodeQualification({ ...oeQualification, roleId: '9001' }));
  assert.throws(() => decodeQualification({ ...clientQualification, roleId: 'AE001' }));
  assert.throws(() => decodeQualification({ ...oeQualification, roleType: 'RELATED_PERSON' }));
  assert.throws(() => decodeQualification({ ...oeQualification, version: 2 }));
  assert.throws(() => decodeQualification({ ...oeQualification, validTo: 'never' }));
  assert.throws(() => decodeQualification(oeQualification, '6002'));
});

test('qualification lists: page rows carry a nullable subject name; subject lists must belong to that subject', () => {
  const page = decodeQualificationPage({ items: [{ qualification: oeQualification, subjectName: 'PayFac AE LLC' }, { qualification: clientQualification, subjectName: null }],
    page: 1, size: 20, total: 2 });
  assert.deepEqual(page.items.map(i => i.subjectName), ['PayFac AE LLC', null]);
  assert.throws(() => decodeQualificationPage({ items: [{ qualification: oeQualification, subjectName: null }, { qualification: oeQualification, subjectName: null }], page: 1, size: 20, total: 2 }));
  assert.throws(() => decodeQualificationPage({ items: [], page: 1, size: 500, total: 0 }));
  assert.equal(decodeQualifications([oeQualification], { roleType: 'OPERATING_ENTITY', roleId: 'AE001' }).length, 1);
  assert.throws(() => decodeQualifications([oeQualification], { roleType: 'OPERATING_ENTITY', roleId: 'SA001' }));
});

test('subject lookup and history decoders reject foreign data', () => {
  assert.deepEqual(decodeSubject({ roleType: 'LOCAL_PARTNER', roleId: '9001', displayName: 'Our PayFac', status: 'ACTIVE' }, 'LOCAL_PARTNER', '9001'),
    { roleType: 'LOCAL_PARTNER', roleId: '9001', displayName: 'Our PayFac', status: 'ACTIVE' });
  assert.throws(() => decodeSubject({ roleType: 'LOCAL_PARTNER', roleId: '9002', displayName: null, status: 'ACTIVE' }, 'LOCAL_PARTNER', '9001'));
  const item = { changeId: '6201', aggregateVersion: '1', fromStatus: null, toStatus: 'APPROVED', triggerType: 'OPERATIONS_MANUAL', reasonCode: 'UNDERWRITING_DONE',
    actorId: '7001', occurredAt: '2026-10-24T00:00:00.000000Z', decisionId: '6101', decisionSource: 'MANUAL', outcome: 'APPROVED', decidedBy: '7001',
    basisNote: 'Acquirer letter', validFrom: '2026-10-24T00:00:00.000000Z', validTo: null };
  assert.equal(decodeHistory({ qualificationId: '6001', items: [item] }, '6001').items[0].triggerType, 'OPERATIONS_MANUAL');
  assert.throws(() => decodeHistory({ qualificationId: '6002', items: [item] }, '6001'));
  assert.throws(() => decodeHistory({ qualificationId: '6001', items: [{ ...item, aggregateVersion: 1 }] }, '6001'));
});

test('manual decision: only the role\'s allowed types, expectedVersion from the existing row, UTC micros and end after start', () => {
  assert.deepEqual(allowedQualificationTypes('CLIENT'), ['INDIVIDUAL_KYC', 'ORGANIZATION_KYB']);
  assert.deepEqual(allowedQualificationTypes('OPERATING_ENTITY'), ['ENTITY_UNDERWRITING']);
  assert.deepEqual(allowedQualificationTypes('LOCAL_PARTNER'), ['PARTNER_DUE_DILIGENCE']);
  assert.deepEqual(allowedQualificationTypes('RELATED_PERSON'), []);
  const from = new Date('2026-10-24T08:00:00.000Z');
  const draft = { roleType: 'OPERATING_ENTITY' as const, roleId: 'AE001', qualificationType: 'ENTITY_UNDERWRITING', validFrom: from, validTo: null,
    reasonCode: ' UNDERWRITING_DONE ', basisNote: ' Acquirer letter ' };
  assert.deepEqual(manualDecisionPayload(draft, null), { roleType: 'OPERATING_ENTITY', roleId: 'AE001', qualificationType: 'ENTITY_UNDERWRITING',
    validFrom: '2026-10-24T08:00:00.000000Z', validTo: null, reasonCode: 'UNDERWRITING_DONE', basisNote: 'Acquirer letter', expectedVersion: null });
  assert.equal(manualDecisionPayload(draft, decodeQualification(oeQualification))?.expectedVersion, '2');
  assert.equal(manualDecisionPayload({ ...draft, qualificationType: 'PARTNER_DUE_DILIGENCE' }, null), null);
  assert.equal(manualDecisionPayload({ ...draft, validTo: from }, null), null);
  assert.equal(manualDecisionPayload(draft, decodeQualification(clientQualification)), null);
  assert.equal(utcMicros(new Date('2026-10-24T08:00:00.123Z')), '2026-10-24T08:00:00.123000Z');
  assert.deepEqual(revocationPayload(decodeQualification(oeQualification), 'WITHDRAWN ', ' Letter withdrawn'),
    { expectedVersion: '2', reasonCode: 'WITHDRAWN', basisNote: 'Letter withdrawn' });
});

test('write buttons: only users holding the MANAGE button register or revoke; revoke needs APPROVED; ended subjects cannot register', () => {
  const approved = decodeQualification(oeQualification); const revoked = decodeQualification({ ...oeQualification, status: 'REVOKED', effectiveStatus: 'REVOKED' });
  const manager = hasButton([button(qualificationManageButton)], qualificationManageButton);
  assert.equal(manager, true);
  assert.equal(hasButton([button('OPERATIONS_OPERATING_ENTITIES_MANAGE')], qualificationManageButton), false);
  assert.deepEqual(qualificationActions(approved, false), { history: true, register: false, revoke: false });
  assert.deepEqual(qualificationActions(approved, manager), { history: true, register: true, revoke: true });
  assert.deepEqual(qualificationActions(revoked, manager), { history: true, register: true, revoke: false });
  assert.deepEqual(qualificationActions(null, manager), { history: false, register: true, revoke: false });
  assert.deepEqual(qualificationActions(null, manager, true), { history: false, register: false, revoke: false });
});

test('subject card slots list every allowed type, unregistered ones as null', () => {
  const slots = subjectQualificationSlots('CLIENT', [decodeQualification(clientQualification)]);
  assert.deepEqual(slots.map(s => [s.qualificationType, s.qualification?.qualificationId ?? null]), [['INDIVIDUAL_KYC', null], ['ORGANIZATION_KYB', '6003']]);
});

test('command failures: version conflict asks for refresh, lock contention, ended subject, invalid and not found have their own text', () => {
  assert.equal(commandFailureKey(new ApiError(409, 'QUALIFICATION_CHANGED')), 'command.conflict');
  assert.equal(commandFailureKey(new ApiError(409, 'QUALIFICATION_NOT_ACTIVE')), 'command.conflict');
  assert.equal(commandFailureKey(new ApiError(409, 'CONCURRENT_REQUEST')), 'command.busy');
  assert.equal(commandFailureKey(new ApiError(422, 'QUALIFICATION_SUBJECT_TERMINATED')), 'command.subjectTerminated');
  assert.equal(commandFailureKey(new ApiError(422, 'QUALIFICATION_INVALID')), 'command.invalid');
  assert.equal(commandFailureKey(new ApiError(404, 'LOCAL_PARTNER_NOT_FOUND')), 'command.notFound');
  assert.equal(commandFailureKey(new ApiError(503, 'SERVICE_UNAVAILABLE')), null);
  assert.equal(commandFailureKey(undefined), null);
  for (const key of ['command.conflict', 'command.busy', 'command.subjectTerminated', 'command.invalid', 'command.notFound']) assert.ok(t.has(key), key);
  assert.equal(t('decision.impact'), 'New order resolutions will treat this subject as qualified.');
});

test('URL query keeps only valid values; the detail-page link prefills the subject', () => {
  assert.deepEqual(readListQuery('?roleType=OPERATING_ENTITY&roleId=ae001&status=APPROVED&qualificationType=ENTITY_UNDERWRITING'),
    { roleType: 'OPERATING_ENTITY', roleId: 'AE001', status: 'APPROVED', qualificationType: 'ENTITY_UNDERWRITING' });
  assert.deepEqual(readListQuery('?roleType=CLIENT&roleId=AE001&status=ACTIVE'), { roleType: 'CLIENT' });
  assert.deepEqual(readListQuery('?roleId=9001'), {});
  assert.equal(qualificationLink('LOCAL_PARTNER', '9001'), '/compliance/qualifications?roleType=LOCAL_PARTNER&roleId=9001');
  assert.deepEqual(readListQuery(qualificationLink('LOCAL_PARTNER', '9001').split('?')[1]), { roleType: 'LOCAL_PARTNER', roleId: '9001' });
});

test('labels: every known code has an English name, unknown codes stay raw', () => {
  const groups: [string, readonly string[]][] = [['enum.roleType', roleTypes], ['enum.qualificationType', qualificationTypes], ['enum.status', qualificationStatuses],
    ['enum.effectiveStatus', effectiveStatuses], ['enum.decisionSource', decisionSources], ['enum.triggerType', triggerTypes]];
  for (const [prefix, codes] of groups) for (const code of codes) assert.ok(t.has(`${prefix}.${code}`), `${prefix}.${code}`);
  assert.equal(enumLabel(t, 'enum.effectiveStatus', 'ELIGIBLE'), 'Eligible');
  assert.equal(enumLabel(t, 'enum.effectiveStatus', 'FUTURE_STATUS'), 'FUTURE_STATUS');
});

test('page registration matches Platform block 137: route, componentKey and the MANAGE button', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_QUALIFICATIONS');
  assert.equal(page?.frontendRoute, qualificationRoute);
  assert.deepEqual(page?.actions.map(a => a.resourceCode), [qualificationManageButton]);
  const menu = { resourceId: '1', parentResourceId: null, resourceType: 'MENU', resourceCode: 'OPERATIONS_QUALIFICATIONS', displayName: '',
    componentKey: 'OPERATIONS_QUALIFICATIONS', frontendRoute: qualificationRoute, sortOrder: 90 } as unknown as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu), [qualificationRoute]);
  // Platform block 137 (requirements/20261023/commercial-商业关系快照与真实解析接入/db/platform-support/registration-block137.sql)
  const block137: [string, string, string][] = [['OPERATIONS_QUALIFICATIONS', '/compliance/qualifications', 'OPERATIONS_QUALIFICATIONS_MANAGE'],
    ['OPERATIONS_OPERATING_ENTITIES', '/master-data/operating-entities', 'OPERATIONS_OPERATING_ENTITIES_MANAGE'],
    ['OPERATIONS_LOCAL_PARTNERS', '/master-data/local-partners', 'OPERATIONS_LOCAL_PARTNERS_MANAGE']];
  for (const [componentKey, route, manage] of block137) {
    const registered = pageCapabilities.find(p => p.componentKey === componentKey);
    assert.equal(registered?.frontendRoute, route, componentKey);
    assert.deepEqual(registered?.actions.map(a => a.resourceCode), [manage], componentKey);
  }
});
