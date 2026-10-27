import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeCode, decodeCodes, decodeIssuedCode, decodePartnerOptions, effectiveStatus, issueIssues, issuePayload } from './onboardingCodes.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('client-review');
const code = { codeId: '7101', localPartnerId: '9001', codeMasked: '****-7Q2K', status: 'ACTIVE', effectiveFrom: '2026-10-27T00:00:00.000000Z',
  effectiveTo: null, maxUses: 50, useCount: 3, note: null, issuedBy: '501', issuedTime: '2026-10-27T00:00:00.000000Z', revokedBy: null, revokedTime: null };

test('codes belong to the requested partner and the list never carries the plain code', () => {
  assert.equal(decodeCode(code, '9001').useCount, 3);
  assert.throws(() => decodeCode(code, '9002'));
  assert.throws(() => decodeCode({ ...code, codeMasked: 'ABCD-7Q2K' }, '9001'), 'a plain code in the list is rejected');
  const older = { ...code, codeId: '7100', issuedTime: '2026-10-26T00:00:00.000000Z' };
  assert.deepEqual(decodeCodes({ items: [older, code] }, '9001').map(c => c.codeId), ['7101', '7100']);
  assert.throws(() => decodeCodes({ items: [code, code] }, '9001'));
});

test('the issue response carries the plain XXXX-XXXX code exactly once', () => {
  assert.equal(decodeIssuedCode({ issued: code, code: 'AB12-7Q2K' }, '9001').code, 'AB12-7Q2K');
  assert.throws(() => decodeIssuedCode({ issued: code, code: 'ab12-7q2k' }, '9001'));
  assert.throws(() => decodeIssuedCode({ issued: { ...code, localPartnerId: '9002' }, code: 'AB12-7Q2K' }, '9001'));
  assert.throws(() => decodeIssuedCode(code, '9001'));
});

test('issue form: optional expiry in the future, optional bounded uses', () => {
  const now = Date.parse('2026-10-27T00:00:00Z');
  assert.deepEqual(issueIssues({ effectiveTo: '2026-11-27T00:00:00.000Z', maxUses: 10 }, now), []);
  assert.deepEqual(issueIssues({}, now), []);
  assert.deepEqual(issueIssues({ effectiveTo: '2026-10-26T00:00:00.000Z' }, now), [t('codes.issue.pastExpiry')]);
  assert.deepEqual(issueIssues({ maxUses: 0 }, now), [t('codes.issue.maxUses')]);
  assert.deepEqual(issuePayload({ effectiveTo: null, maxUses: undefined, note: '  ' }), { effectiveFrom: null, effectiveTo: null, maxUses: null, note: null });
});

test('partner options are distinct OE providers', () => {
  assert.equal(decodePartnerOptions({ items: [{ localPartnerId: '9001', partnerNo: 'LP9001', legalName: 'Our PayFac', status: 'ACTIVE' }] })[0].partnerNo, 'LP9001');
  assert.throws(() => decodePartnerOptions({ items: [{ localPartnerId: 'x', partnerNo: 'LP', legalName: null, status: 'ACTIVE' }] }));
});

test('a stored ACTIVE code past its end time or out of uses is shown as EXPIRED', () => {
  const now = Date.parse('2026-10-27T12:00:00Z');
  assert.equal(effectiveStatus({ ...code, effectiveTo: '2026-10-27T00:00:00.000000Z' }, now), 'EXPIRED');
  assert.equal(effectiveStatus({ ...code, effectiveTo: '2026-10-28T00:00:00.000000Z' }, now), 'ACTIVE');
  assert.equal(effectiveStatus({ ...code, maxUses: 3, useCount: 3 }, now), 'EXPIRED');
  assert.equal(effectiveStatus({ ...code, status: 'REVOKED', effectiveTo: '2026-10-27T00:00:00.000000Z' }, now), 'REVOKED', 'revoked stays revoked');
});
