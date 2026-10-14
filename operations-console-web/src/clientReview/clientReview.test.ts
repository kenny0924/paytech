import test from 'node:test';
import assert from 'node:assert/strict';
import { canApprove, classificationIssues, decodeCase, decodeCasePage, decodeCommand, decodeDetail } from './clientReview.ts';

const reviewCase = {
  caseId: '7001', caseNo: 'DD7001', applicationId: '8001', clientId: '8101', sourceSubmissionId: 'sub-1', qualificationType: 'ORGANIZATION_KYB',
  status: 'SUBMITTED', version: '1', submittedAt: '2026-10-13T00:00:00.000000Z', decidedAt: null, publicNote: null, returnSection: null,
  subjects: [{ caseId: '7001', caseStatus: 'SUBMITTED', partyId: '9001', profileId: '9101', roleType: 'CLIENT', qualificationType: 'ORGANIZATION_KYB',
    dependencyTypes: [], requirements: [{ requirementId: '9201', requirementCode: 'REGISTRATION', documentType: 'REGISTRATION_CERTIFICATE', required: true,
      status: 'RECEIVED', evidence: [{ evidenceId: '9301', fileId: '9401', status: 'RECEIVED' }] }] }],
  classifications: [], approvalBlockers: ['EVIDENCE_PENDING'],
};
const application = {
  applicationId: '8001', applicationNo: 'APP8001', clientId: '8101', clientType: 'ORGANIZATION', subjectPartyId: '9001', status: 'UNDER_REVIEW',
  savedAt: '2026-10-13T00:00:00.000000Z', editable: false, values: { organization: { legalName: 'Synthetic Co', tradeName: null }, contact: {} },
  people: [], relations: [], documents: [{ documentId: '9501', subjectPartyId: '9001', requirementCode: 'REGISTRATION', documentType: 'REGISTRATION_CERTIFICATE', fileId: '9401', subjectInScope: true }],
};

test('detail keeps owner facts and availability', () => {
  const detail = decodeDetail({ review: reviewCase, applicationStatus: 'AVAILABLE', application, classificationStatus: 'UNAVAILABLE', classification: null,
    industriesStatus: 'AVAILABLE', industries: [{ code: 'RETAIL', label: '零售' }] }, '7001');
  assert.equal(detail.application?.values.organization.legalName, 'Synthetic Co');
  assert.equal(detail.classification, null);
  assert.equal(detail.review.subjects[0].requirements[0].evidence[0].fileId, '9401');
});

test('detail rejects mismatched case, application or inconsistent availability', () => {
  assert.throws(() => decodeDetail({ review: reviewCase, applicationStatus: 'AVAILABLE', application, classificationStatus: 'UNAVAILABLE', classification: null,
    industriesStatus: 'AVAILABLE', industries: [] }, '7002'));
  assert.throws(() => decodeDetail({ review: reviewCase, applicationStatus: 'AVAILABLE', application: { ...application, applicationId: '8002' },
    classificationStatus: 'UNAVAILABLE', classification: null, industriesStatus: 'AVAILABLE', industries: [] }, '7001'));
  assert.throws(() => decodeDetail({ review: reviewCase, applicationStatus: 'UNAVAILABLE', application, classificationStatus: 'UNAVAILABLE', classification: null,
    industriesStatus: 'AVAILABLE', industries: [] }, '7001'));
});

test('approval follows Risk blockers only', () => {
  assert.equal(canApprove(decodeCase(reviewCase)), false);
  assert.equal(canApprove(decodeCase({ ...reviewCase, approvalBlockers: [] })), true);
  assert.equal(canApprove(decodeCase({ ...reviewCase, status: 'RETURNED', approvalBlockers: [] })), false);
});

test('page and command decoders reject foreign data', () => {
  assert.equal(decodeCasePage({ items: [reviewCase], page: 1, size: 20, total: 1 }).items.length, 1);
  assert.throws(() => decodeCasePage({ items: [reviewCase, reviewCase], page: 1, size: 20, total: 2 }));
  assert.throws(() => decodeCommand({ caseId: '7002', status: 'RETURNED' }, '7001'));
  assert.throws(() => decodeCase({ ...reviewCase, returnSection: 'OTHER' }));
});

test('classification draft checks', () => {
  assert.deepEqual(classificationIssues([{ industryCode: 'RETAIL', mccCode: '5411', primary: true }]), []);
  assert.ok(classificationIssues([]).includes('至少添加一条分类'));
  assert.ok(classificationIssues([{ industryCode: 'RETAIL', mccCode: '5411', primary: false }]).includes('必须恰好有一个主要分类'));
  assert.ok(classificationIssues([{ industryCode: 'RETAIL', mccCode: '54A1', primary: true }]).includes('MCC 必须是四位数字'));
  assert.ok(classificationIssues([{ industryCode: 'RETAIL', mccCode: '5411', primary: true }, { industryCode: 'RETAIL', mccCode: '5411', primary: false }])
    .includes('行业与 MCC 组合不能重复'));
});
