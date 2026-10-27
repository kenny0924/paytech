import test from 'node:test';
import assert from 'node:assert/strict';
import { canApprove, canWaive, caseStatuses, checkAwaitingResult, checkOutcomes, classificationIssues, decodeCase, decodeCasePage, decodeCommand, decodeDetail, decodeFileDownload,
  decodeResponsiblePartners, evidenceRejectReasons, fourEyesBlocked, isNotReady, rejectionBlocker, unmetConditions, hitResolutions, partnerName, recommendations, remediationCodes, returnErrorCodes, returnErrorIssues,
  returnPayload, returnSections, riskFactorCodes, riskRatingIssues, riskRatings, suggestedReturnErrors, withOnBehalf, workbenchCounts } from './clientReview.ts';
import { enumLabel } from '../i18n/index.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('client-review');

const reviewCase = {
  caseId: '7001', caseNo: 'DD7001', applicationId: '8001', clientId: '8101', sourceSubmissionId: 'sub-1', qualificationType: 'ORGANIZATION_KYB',
  status: 'SUBMITTED', version: '1', submittedAt: '2026-10-13T00:00:00.000000Z', decidedAt: null, publicNote: null, returnSection: null,
  subjects: [{ caseId: '7001', caseStatus: 'SUBMITTED', partyId: '9001', profileId: '9101', roleType: 'CLIENT', qualificationType: 'ORGANIZATION_KYB',
    dependencyTypes: [], requirements: [{ requirementId: '9201', requirementCode: 'REGISTRATION', documentTypes: ['REGISTRATION_CERTIFICATE'], required: true,
      status: 'RECEIVED', evidence: [{ evidenceId: '9301', fileAddress: '/files/v1/9401', status: 'RECEIVED' }] }] }],
  classifications: [], approvalBlockers: ['EVIDENCE_PENDING'],
};
const application = {
  applicationId: '8001', applicationNo: 'APP8001', clientId: '8101', clientType: 'ORGANIZATION', subjectPartyId: '9001', status: 'UNDER_REVIEW',
  savedAt: '2026-10-13T00:00:00.000000Z', editable: false, values: { organization: { legalName: 'Synthetic Co', tradeName: null }, contact: {} },
  people: [], relations: [], documents: [{ documentId: '9501', subjectPartyId: '9001', requirementCode: 'REGISTRATION', documentType: 'REGISTRATION_CERTIFICATE', fileAddress: '/files/v1/9401', subjectInScope: true }],
};

test('detail keeps owner facts and availability', () => {
  const detail = decodeDetail({ review: reviewCase, applicationStatus: 'AVAILABLE', application, classificationStatus: 'UNAVAILABLE', classification: null,
    industriesStatus: 'AVAILABLE', industries: [{ code: 'RETAIL', label: '零售' }] }, '7001');
  assert.equal(detail.application?.values.organization.legalName, 'Synthetic Co');
  assert.equal(detail.classification, null);
  assert.equal(detail.review.subjects[0].requirements[0].evidence[0].fileId, '9401');
  assert.equal(detail.application?.documents[0].fileId, '9401', 'document file id comes from fileAddress');
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
  assert.deepEqual(classificationIssues([]), [t('classification.issue.empty'), t('classification.issue.onePrimary')]);
  assert.ok(classificationIssues([]).includes('Add at least one classification'));
  assert.deepEqual(classificationIssues([{ industryCode: 'RETAIL', mccCode: '5411', primary: false }]), [t('classification.issue.onePrimary')]);
  assert.deepEqual(classificationIssues([{ industryCode: 'RETAIL', mccCode: '54A1', primary: true }]), [t('classification.issue.mccFormat')]);
  assert.deepEqual(classificationIssues([{ industryCode: '', mccCode: '5411', primary: true }]), [t('classification.issue.industryRequired')]);
  assert.deepEqual(classificationIssues(Array.from({ length: 21 }, (_, i) => ({ industryCode: 'RETAIL', mccCode: String(5000 + i), primary: i === 0 }))),
    [t('classification.issue.tooMany')]);
  assert.deepEqual(classificationIssues([{ industryCode: 'RETAIL', mccCode: '5411', primary: true }, { industryCode: 'RETAIL', mccCode: '5411', primary: false }]),
    [t('classification.issue.duplicate')]);
});

test('labels: known review codes translate, unknown codes and fields stay raw', () => {
  assert.equal(enumLabel(t, 'enum.caseStatus', 'IN_REVIEW'), 'In Review');
  assert.equal(enumLabel(t, 'enum.blocker', 'EVIDENCE_PENDING'), "Some documents haven't been verified");
  assert.equal(enumLabel(t, 'enum.blocker', 'FUTURE_BLOCKER'), 'FUTURE_BLOCKER');
  assert.equal(enumLabel(t, 'application.field', 'legalName'), 'Legal Name');
  assert.equal(enumLabel(t, 'application.field', 'unknownField'), 'unknownField');
  for (const status of caseStatuses) assert.ok(t.has('enum.caseStatus.' + status), status);
  for (const section of returnSections) assert.ok(t.has('enum.section.' + section), section);
});

test('file download grant must match the requested file and use HTTPS', () => {
  const grant = { fileId: '9401', displayName: 'registration.pdf', mimeType: 'application/pdf', sizeBytes: '1024',
    url: 'https://storage.local.test/obj?sig=1', expiresAt: '2026-10-14T00:01:00.123456Z' };
  assert.equal(decodeFileDownload(grant, '9401').url, grant.url);
  assert.throws(() => decodeFileDownload(grant, '9402'));
  assert.throws(() => decodeFileDownload({ ...grant, url: 'http://storage.local.test/obj' }, '9401'));
  assert.throws(() => decodeFileDownload({ ...grant, expiresAt: 'soon' }, '9401'));
});

const workbenchCase = {
  ...reviewCase, status: 'IN_REVIEW', caseType: 'ONBOARDING', jurisdictionCode: 'SA', tier: 'STANDARD', riskRating: null, responsiblePartnerId: '9001', operatedBy: 'PLATFORM',
  onBehalfOfPartnerId: '9001', roundNo: 2, previousCaseId: '6999', reviewDueTime: null, fourEyesRequired: true, recommendation: 'APPROVE',
  recommendedBy: '501', recommendedAt: '2026-10-27T01:00:00.000000Z', recommendationNote: 'Documents verified',
  subjects: [{ ...reviewCase.subjects[0], uboBasis: null, jobTitle: null, requirements: [
    { ...reviewCase.subjects[0].requirements[0], status: 'REJECTED', waivable: true, maxAgeDays: 90, waivedBy: null, waivedReasonCode: null,
      evidence: [{ evidenceId: '9301', fileAddress: '/files/v1/9401', status: 'REJECTED', documentType: 'REGISTRATION_CERTIFICATE', rejectReasonCode: 'DOCUMENT_EXPIRED',
        reviewerId: '501', reviewedAt: '2026-10-27T00:30:00.000000Z' }] },
    { requirementId: '9202', requirementCode: 'PROOF_OF_ADDRESS', documentTypes: ['UTILITY_BILL'], required: true, status: 'PENDING', evidence: [] },
    { requirementId: '9203', requirementCode: 'BANK_LETTER', documentTypes: ['BANK_LETTER'], required: false, status: 'PENDING', evidence: [] }],
    checks: [{ checkId: '9601', checkCode: 'REGISTRY_REVIEW', checkType: 'REGISTRY', checkMode: 'MANUAL', blocking: true, required: true, status: 'REQUESTED',
      outcome: null, reasonCode: null, note: null, reviewerId: null, reviewedAt: null }],
    screenings: [{ runId: '9701', screeningType: 'SANCTIONS', providerCode: 'MOCK', status: 'SUCCEEDED', triggerSource: 'CASE',
      startedAt: '2026-10-27T00:00:00.000000Z', completedAt: '2026-10-27T00:00:01.000000Z',
      hits: [{ hitId: '9801', externalHitRef: 'MOCK-1', entityType: 'PERSON', matchType: 'NAME', matchScore: '0.92', riskLevel: 'HIGH', listTypes: ['PEP'], status: 'OPEN', resolutions: [] }] }] }],
  riskFactors: [], timeline: [{ caseId: '7001', actionType: 'SUBMIT', fromStatus: null, toStatus: 'SUBMITTED', actorId: null, operatedBy: null,
    onBehalfOfPartnerId: null, reasonCode: null, occurredAt: '2026-10-27T00:00:00.000000Z' }], returnErrors: [],
};

test('workbench facts decode; older cases without CP-5 fields still decode with empty defaults', () => {
  const decoded = decodeCase(workbenchCase);
  assert.equal(decoded.roundNo, 2);
  assert.equal(decoded.screenings[0].hits[0].listTypes[0], 'PEP');
  assert.equal(decoded.screenings[0].subjectPartyId, '9001', 'per-subject runs carry their subject');
  assert.equal(decoded.checks[0].caseId, '7001');
  assert.equal(decoded.subjects[0].requirements[0].evidence[0].fileId, '9401', 'file id comes from the file service address');
  assert.equal(decoded.recommendedTime, '2026-10-27T01:00:00.000000Z');
  assert.throws(() => decodeCase({ ...workbenchCase, subjects: [{ ...workbenchCase.subjects[0], requirements: [{ ...workbenchCase.subjects[0].requirements[0],
    evidence: [{ evidenceId: '9301', fileAddress: 'https://x/9401', status: 'RECEIVED' }] }] }] }), 'foreign file address');
  assert.equal(decoded.subjects[0].requirements[0].evidence[0].rejectReasonCode, 'DOCUMENT_EXPIRED');
  assert.equal(decoded.subjects[0].requirements[1].waivable, false);
  const legacy = decodeCase(reviewCase);
  assert.deepEqual([legacy.checks, legacy.screenings, legacy.riskFactors, legacy.timeline, legacy.fourEyesRequired, legacy.responsiblePartnerId], [[], [], [], [], false, null]);
  assert.throws(() => decodeCase({ ...workbenchCase, responsiblePartnerId: 'payfac' }));
  assert.throws(() => decodeCase({ ...workbenchCase, roundNo: 0 }));
  assert.throws(() => decodeCase({ ...workbenchCase, returnErrors: [{ requirementKey: 'bad key', subjectPartyId: null, code: 'FIELD_MISSING', reason: null, remediation: 'PROVIDE_FIELD' }] }));
});

test('four-eyes: the recommender cannot approve; another reviewer can once blockers are cleared', () => {
  const ready = decodeCase({ ...workbenchCase, approvalBlockers: [] });
  assert.equal(fourEyesBlocked(ready, '501'), true);
  assert.equal(canApprove(ready, '501'), false);
  assert.equal(canApprove(ready, '502'), true);
  assert.equal(canApprove(decodeCase({ ...workbenchCase, approvalBlockers: [], fourEyesRequired: false }), '501'), true);
  assert.equal(canApprove(decodeCase({ ...workbenchCase, approvalBlockers: [], status: 'WAITING_EVIDENCE' }), '502'), true);
});

test('workbench counts, waivable requirements and manual checks awaiting results', () => {
  const decoded = decodeCase(workbenchCase);
  assert.deepEqual(workbenchCounts(decoded), { evidence: 0, checks: 1, hits: 1 });
  assert.equal(canWaive(decoded.subjects[0].requirements[0]), true);
  assert.equal(canWaive(decoded.subjects[0].requirements[1]), false, 'not waivable by programme');
  assert.equal(checkAwaitingResult(decoded.checks[0]), true);
  assert.equal(checkAwaitingResult({ ...decoded.checks[0], checkMode: 'AUTOMATED' }), false);
  assert.equal(checkAwaitingResult({ ...decoded.checks[0], status: 'COMPLETED', outcome: 'PASS' }), false);
});

test('return drafts come from rejected and missing required documents and serialize per requirement', () => {
  const drafts = suggestedReturnErrors(decodeCase(workbenchCase));
  assert.deepEqual(drafts.map(d => [d.requirementKey, d.code, d.remediation]), [
    ['document.REGISTRATION', 'DOCUMENT_EXPIRED', 'UPLOAD_DOCUMENT'], ['document.PROOF_OF_ADDRESS', 'DOCUMENT_MISSING', 'UPLOAD_DOCUMENT']]);
  assert.deepEqual(returnErrorIssues(drafts), []);
  assert.deepEqual(returnErrorIssues([]), [t('return.issue.empty')]);
  assert.deepEqual(returnErrorIssues([...drafts, drafts[0]]), [t('return.issue.duplicate')]);
  assert.deepEqual(returnErrorIssues([{ ...drafts[0], requirementKey: 'has space' }]), [t('return.issue.keyFormat')]);
  assert.deepEqual(returnErrorIssues([{ ...drafts[0], requirementKey: 'document:X' }]), [t('return.issue.keyFormat')]);
  const payload = returnPayload([{ ...drafts[0], reason: '  Upload the 2026 certificate ' }, drafts[1]], 14, ' Please fix ', ' DOCS_OUTDATED ');
  assert.deepEqual(payload, { reasonCode: 'DOCS_OUTDATED', dueDays: 14, publicNote: 'Please fix', errors: [
    { requirementKey: 'document.REGISTRATION', subjectPartyId: '9001', code: 'DOCUMENT_EXPIRED', reason: 'Upload the 2026 certificate', remediation: 'UPLOAD_DOCUMENT' },
    { requirementKey: 'document.PROOF_OF_ADDRESS', subjectPartyId: '9001', code: 'DOCUMENT_MISSING', reason: null, remediation: 'UPLOAD_DOCUMENT' }] });
});

test('risk rating draft requires a rating and distinct complete factors; writes carry the on-behalf PayFac', () => {
  assert.deepEqual(riskRatingIssues('HIGH', [{ factorCode: 'PEP', factorValue: 'Yes' }], 'PEP confirmed'), []);
  assert.deepEqual(riskRatingIssues('HIGH', [{ factorCode: 'PEP', factorValue: 'Yes' }], ' '), [t('risk.issue.noteRequired')]);
  assert.deepEqual(riskRatingIssues(undefined, []), [t('risk.issue.ratingRequired'), t('risk.issue.factorRequired')]);
  assert.deepEqual(riskRatingIssues('LOW', [{ factorCode: 'PEP', factorValue: ' ' }]), [t('risk.issue.factorIncomplete')]);
  assert.deepEqual(riskRatingIssues('LOW', [{ factorCode: 'PEP', factorValue: 'Yes' }, { factorCode: 'PEP', factorValue: 'Yes ' }]), [t('risk.issue.duplicate')]);
  assert.deepEqual(withOnBehalf({ recommendation: 'APPROVE' }, '9001'), { recommendation: 'APPROVE', onBehalfOfPartnerId: '9001' });
  assert.deepEqual(withOnBehalf({}, null), { onBehalfOfPartnerId: null });
});

test('responsible PayFac options decode once and resolve names without per-row requests', () => {
  const partners = decodeResponsiblePartners({ items: [{ localPartnerId: '9001', partnerNo: 'LP9001', legalName: 'Our PayFac' }, { localPartnerId: '9002', partnerNo: 'LP9002', legalName: null }] });
  assert.equal(partnerName(partners, '9001'), 'Our PayFac');
  assert.equal(partnerName(partners, '9002'), 'LP9002');
  assert.equal(partnerName(partners, '9999'), '9999');
  assert.equal(partnerName(partners, null), null);
  assert.throws(() => decodeResponsiblePartners({ items: [partners[0], partners[0]] }));
});

test('new review enum codes all have English labels', () => {
  for (const status of caseStatuses) assert.ok(t.has('enum.caseStatus.' + status), status);
  for (const code of [...evidenceRejectReasons].map(c => 'enum.rejectReason.' + c).concat([...hitResolutions].map(c => 'enum.hitResolution.' + c),
    [...riskRatings].map(c => 'enum.riskRating.' + c), [...riskFactorCodes].map(c => 'enum.riskFactor.' + c), [...returnErrorCodes].map(c => 'enum.errorCode.' + c),
    [...remediationCodes].map(c => 'enum.remediation.' + c), [...recommendations].map(c => 'recommendation.action.' + c), [...checkOutcomes].map(c => 'enum.checkOutcome.' + c))) {
    assert.ok(t.has(code), code);
  }
});

test('four-eyes rejection needs a REJECT recommendation from someone else', () => {
  const base = decodeCase({ ...workbenchCase, approvalBlockers: [] });
  assert.equal(rejectionBlocker({ ...base, fourEyesRequired: false }, '501'), null);
  assert.equal(rejectionBlocker({ ...base, recommendation: null, recommendedBy: null }, '502'), 'RECOMMENDATION_MISSING');
  assert.equal(rejectionBlocker(base, '502'), 'RECOMMENDATION_NOT_REJECT');
  assert.equal(rejectionBlocker({ ...base, recommendation: 'REJECT' }, '501'), 'FOUR_EYES_SAME_PERSON');
  assert.equal(rejectionBlocker({ ...base, recommendation: 'REJECT' }, '502'), null);
  for (const code of ['CHECK_NOT_PASSED', 'SCREENING_INCOMPLETE', 'SCREENING_HIT_OPEN', 'SCREENING_TRUE_MATCH', 'RISK_RATING_MISSING', 'RECOMMENDATION_MISSING',
    'RECOMMENDATION_NOT_APPROVE', 'RECOMMENDATION_NOT_REJECT', 'FOUR_EYES_SAME_PERSON']) assert.ok(t.has('enum.blocker.' + code), code);
});

test('422 NOT_READY shows the refreshed case approvalBlockers, never parsed text', () => {
  assert.equal(isNotReady({ status: 422, code: 'CLIENT_REVIEW_NOT_READY' }), true);
  assert.equal(isNotReady({ status: 409, code: 'CLIENT_REVIEW_CLOSED' }), false);
  assert.equal(isNotReady(undefined), false);
  const refreshed = decodeCase({ ...workbenchCase, approvalBlockers: ['RISK_RATING_MISSING', 'SCREENING_HIT_OPEN'] });
  assert.deepEqual(unmetConditions(refreshed, 'APPROVE', '502'), ['RISK_RATING_MISSING', 'SCREENING_HIT_OPEN']);
  assert.deepEqual(unmetConditions(refreshed, 'REJECT', '502'), ['RECOMMENDATION_NOT_REJECT'], 'reject under four-eyes shows the recommendation condition');
  assert.deepEqual(unmetConditions({ ...refreshed, fourEyesRequired: false, approvalBlockers: ['FOUR_EYES_SAME_PERSON', 'RISK_RATING_MISSING'] }, 'REJECT', '501'),
    ['FOUR_EYES_SAME_PERSON'], 'rejection only lists conditions that apply to rejecting');
});
