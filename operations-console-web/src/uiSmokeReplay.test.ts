import test from 'node:test';
import assert from 'node:assert/strict';
import bodies from './testing/uiSmokeBodies.json' with { type: 'json' };
import { decodeCasePage, decodeDetail, decodeResponsiblePartners, latestCaseId, partnerName } from './clientReview/clientReview.ts';
import { decodeClientDetail, decodeClientPage } from './masterData/clients.ts';
import { decodeCatalogItems, decodeJurisdictions, decodePrograms, decodeSchemas } from './onboardingConfig/onboardingConfig.ts';
import { decodeCodes, decodePartnerOptions } from './onboardingCodes/onboardingCodes.ts';
import { fieldLabel, schemaLabel, valueLabel, valueText } from './onboardingConfig/schemaLabel.ts';
import { testT } from './i18n/testing.ts';
import { formatCountry } from './i18n/index.ts';

/** mor-test ui-smoke 抓取的真实 BFF 200 响应（去重），逐个经页面解码器回放。 */
interface Captured { url: string; requestId: string | null; body: unknown }
const captured = bodies as Captured[];
const tReview = testT('client-review'), tSchema = testT('onboarding-schema');

/** 按 BFF 路径选择页面解码器；返回解码结果，未知路径返回 undefined。 */
function decode(entry: Captured): unknown {
  const [path, raw] = entry.url.split('?'); const query = new URLSearchParams(raw ?? ''); let match: RegExpMatchArray | null;
  if ((match = path.match(/^\/client-onboarding-admin\/v1\/cases\/(\d+)$/))) return decodeDetail(entry.body, match[1]);
  if (path === '/client-onboarding-admin/v1/cases') return decodeCasePage(entry.body);
  if (path.endsWith('/responsible-partners')) return decodeResponsiblePartners(entry.body);
  if ((match = path.match(/^\/client-admin\/v1\/clients\/(\d+)$/))) return decodeClientDetail(entry.body, match[1]);
  if (path === '/client-admin/v1/clients') return decodeClientPage(entry.body);
  if (path === '/onboarding-config-admin/v1/jurisdictions') return decodeJurisdictions(entry.body);
  if (path === '/onboarding-config-admin/v1/onboarding-schemas') return decodeSchemas(entry.body, query.get('jurisdiction')!);
  if (path === '/onboarding-config-admin/v1/catalog-items') return decodeCatalogItems(entry.body, query.get('catalogCode')!);
  if (path === '/onboarding-config-admin/v1/programs') return decodePrograms(entry.body, query.get('jurisdiction')!);
  if (path === '/onboarding-code-admin/v1/local-partners') return decodePartnerOptions(entry.body);
  if ((match = path.match(/^\/onboarding-code-admin\/v1\/local-partners\/(\d+)\/onboarding-codes$/))) return decodeCodes(entry.body, match[1]);
  return undefined;
}

test('every captured mor-test BFF response decodes (cases, client detail, config, codes)', () => {
  const failures = captured.flatMap(entry => { try { decode(entry); return []; } catch (error) { return [`${entry.url} (${entry.requestId}): ${(error as Error).message}`]; } });
  assert.deepEqual(failures, []);
  const paths = new Set(captured.map(entry => entry.url.split('?')[0].replace(/\d{6,}/g, '{id}')));
  for (const path of ['/client-onboarding-admin/v1/cases/{id}', '/client-admin/v1/clients/{id}', '/onboarding-config-admin/v1/onboarding-schemas'])
    assert.ok(paths.has(path), path);
});

test('case detail: document file id comes from fileAddress; industries translate their labelKey', () => {
  const details = captured.filter(entry => /\/cases\/\d+$/.test(entry.url)).map(entry => decode(entry) as ReturnType<typeof decodeDetail>);
  assert.ok(details.length >= 3);
  for (const detail of details) {
    for (const document of detail.application?.documents ?? []) assert.equal(`/files/v1/${document.fileId}`, document.fileAddress);
    const retail = detail.industries.find(option => option.code === 'RETAIL');
    if (retail) assert.equal(schemaLabel(tSchema, retail.labelKey, retail.code), 'Retail');
  }
});

test('every application / client value group and field has a label, never a raw code', () => {
  const groups = new Set<string>(), fields = new Set<string>();
  const collect = (values: Record<string, Record<string, string | null>>) => { for (const [group, entries] of Object.entries(values)) { groups.add(group); Object.keys(entries).forEach(field => fields.add(field)); } };
  for (const entry of captured) {
    if (/\/cases\/\d+$/.test(entry.url)) { const detail = decode(entry) as ReturnType<typeof decodeDetail>; if (detail.application) { collect(detail.application.values); detail.application.people.forEach(p => collect(p.values)); } }
    if (/\/client-admin\/v1\/clients\/\d+$/.test(entry.url)) { const page = decode(entry) as ReturnType<typeof decodeClientDetail>; collect(page.client.values); page.client.people.forEach(p => collect(p.values)); }
  }
  assert.ok(fields.size > 20);
  assert.deepEqual([...groups].filter(group => !tReview.has('application.group.' + group)), []);
  assert.deepEqual([...fields].filter(field => !tReview.has('application.field.' + field)), []);
  assert.equal(valueLabel(tReview, 'field', 'someFutureFieldCode'), 'Some Future Field Code', 'unknown fields fall back to a humanized label');
});

test('catalog values from real bodies show catalog names, not codes; unknown values fall back to the code', () => {
  const detail = decode(captured.find(entry => entry.url.endsWith('/cases/240686862329565184'))!) as ReturnType<typeof decodeDetail>;
  const application = detail.application!; const jurisdiction = detail.review.jurisdictionCode;
  const shown = (group: string, field: string, values = application.values) => valueText(tSchema, group, field, values[group]?.[field] ?? null, jurisdiction);
  assert.equal(shown('business', 'businessModelCode'), tSchema('catalog.BUSINESS_MODEL.PHYSICAL_GOODS'));
  assert.notEqual(shown('business', 'businessModelCode'), 'PHYSICAL_GOODS');
  assert.equal(shown('identifier', 'identifierType'), tSchema('identifierType.AE_TRADE_LICENSE'));
  assert.equal(shown('individual', 'gender', application.people[0].values), tSchema('catalog.GENDER.FEMALE'));
  assert.equal(shown('identifier', 'identifierType', application.people[0].values), tSchema('identifierType.PASSPORT'));
  assert.equal(shown('address', 'stateOrProvince'), tSchema('catalog.STATE_OR_PROVINCE.AE_AZ'), 'AE_AZ is the Abu Dhabi emirate');
  assert.equal(valueText(tSchema, 'business', 'businessModelCode', 'NOT_IN_CATALOG', jurisdiction), 'NOT_IN_CATALOG', 'a value outside the catalog stays as its code');
  assert.equal(fieldLabel(tReview, tSchema, 'attributes', 'AE_TRN'), tSchema('attribute.AE_TRN'));
});

test('a superseded round resolves to the current case of the same application from the real list', () => {
  const historical = decode(captured.find(entry => entry.url.endsWith('/cases/240687064385966080'))!) as ReturnType<typeof decodeDetail>;
  assert.equal(historical.applicationStatus, 'UNAVAILABLE');
  const list = decode(captured.find(entry => entry.url === '/client-onboarding-admin/v1/cases?page=1&size=20')!) as ReturnType<typeof decodeCasePage>;
  assert.equal(latestCaseId(list, historical.review.applicationId), '240687127606710272');
});

test('country codes show localized names; malformed codes stay as is', () => {
  assert.equal(formatCountry('AE', 'en'), 'United Arab Emirates');
  assert.equal(formatCountry('GB', 'en'), 'United Kingdom');
  assert.equal(formatCountry('AE', 'zh-CN'), '阿拉伯联合酋长国');
  assert.equal(formatCountry('LEGACY', 'en'), 'LEGACY');
  assert.equal(formatCountry(null, 'en'), '—');
  const detail = decode(captured.find(entry => entry.url.endsWith('/cases/240686862329565184'))!) as ReturnType<typeof decodeDetail>;
  const person = detail.application!.people[0].values;
  assert.equal(valueText(tSchema, 'organization', 'incorporationCountry', detail.application!.values.organization.incorporationCountry, 'AE'), formatCountry('AE'));
  assert.equal(valueText(tSchema, 'identifier', 'issuingCountry', person.identifier.issuingCountry, 'AE'), formatCountry('GB'));
});

test('Client detail resolves the responsible PayFac name from the captured options, falling back to the ID', () => {
  const page = captured.filter(entry => /\/client-admin\/v1\/clients\/\d+$/.test(entry.url))
    .map(entry => decode(entry) as ReturnType<typeof decodeClientDetail>).find(item => item.client.summary.responsiblePartnerId !== null)!;
  const options = decodeResponsiblePartners(captured.find(entry => entry.url === '/client-admin/v1/responsible-partners')!.body);
  const id = page.client.summary.responsiblePartnerId!;
  assert.notEqual(partnerName(options, id), id, 'the PayFac is shown by name');
  assert.equal(partnerName([], id), id);
});
