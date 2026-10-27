import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeClientDetail, decodeClientPage } from './clients.ts';

const summary = { clientId: '8101', clientNo: 'CL8101', clientType: 'ORGANIZATION', status: 'ACTIVE', jurisdictionCode: 'SA', responsiblePartnerId: '9001',
  legalName: 'Synthetic Co', latestApplicationId: '8001', latestApplicationStatus: 'UNDER_REVIEW', createdAt: '2026-10-27T00:00:00.000000Z' };
const client = { client: summary,
  values: { organization: { legalName: 'Synthetic Co' }, identifier: { identifierType: 'SA_CR', identifierMasked: '******7890' }, contact: { email: 'a***@example.test' } },
  people: [{ partyId: '880000000004', values: { individual: { givenName: 'Test', familyName: 'Person' } } }],
  relations: [{ relationId: '8301', relatedPartyId: '880000000004', relationType: 'UBO', ownershipPercentage: '60', uboBasis: 'OWNERSHIP', jobTitle: null, status: 'ACTIVE' }],
  applications: [{ applicationId: '8001', applicationNo: 'APP8001', status: 'UNDER_REVIEW', jurisdictionCode: 'SA', organizationTypeCode: 'LLC', schemaId: '6101',
    responsiblePartnerId: '9001', submissionRound: 1, dueDiligenceCaseId: '7001', qualificationDecisionId: null, submittedAt: '2026-10-26T01:00:00.000000Z',
    updatedAt: '2026-10-26T01:00:00.000000Z' }] };

test('client detail keeps masked values, relations and applications linked to review cases', () => {
  const page = decodeClientDetail({ client, qualificationsStatus: 'AVAILABLE', qualifications: [] }, '8101');
  assert.equal(page.client.values.identifier.identifierMasked, '******7890');
  assert.equal(page.client.relations[0].relatedPartyId, page.client.people[0].partyId);
  assert.equal(page.client.applications[0].dueDiligenceCaseId, '7001');
  assert.throws(() => decodeClientDetail({ client: { ...client, values: { identifier: { identifierValue: '1234567890' } } }, qualificationsStatus: 'AVAILABLE', qualifications: [] }, '8101'),
    'a raw identifier value is rejected');
  assert.throws(() => decodeClientDetail({ client, qualificationsStatus: 'AVAILABLE', qualifications: [] }, '8102'));
});

test('a client without an approved profile has empty values and people', () => {
  const page = decodeClientDetail({ client: { client: summary, values: null, people: [], relations: [], applications: [] }, qualificationsStatus: 'UNAVAILABLE', qualifications: [] }, '8101');
  assert.deepEqual(page.client.values, {});
});

test('client page decodes paging and rejects duplicates', () => {
  assert.equal(decodeClientPage({ items: [summary], page: 1, size: 20, total: 1 }).items[0].latestApplicationStatus, 'UNDER_REVIEW');
  assert.throws(() => decodeClientPage({ items: [summary, summary], page: 1, size: 20, total: 2 }));
});
