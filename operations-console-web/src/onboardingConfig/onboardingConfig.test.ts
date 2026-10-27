import test from 'node:test';
import assert from 'node:assert/strict';
import { catalogSource, catalogUsages, decodeCatalogItems, decodeJurisdictions, decodePrograms, decodeSchemas, groupFields, programInUse, referenceCatalogHref,
  referencedCatalogs } from './onboardingConfig.ts';

const field = (fieldKey: string, section: string, displayGroup: string | null, displayOrder: number, catalogCode: string | null = null) => ({ section, displayGroup, fieldKey,
  widgetCode: 'TEXT', catalogCode, requirementLevel: 'REQUIRED', requirementGroup: null, appliesToSubject: null, validationPattern: null, minLength: null, maxLength: null,
  visibleWhen: null, labelKey: `onboardingSchema.field.${fieldKey}`, helpKey: null, displayOrder });

test('jurisdictions decode with rule settings and reject duplicates', () => {
  const sa = { jurisdictionCode: 'SA', countryCode: 'SA', labelKey: 'onboardingSchema.jurisdiction.SA', addressFormatCode: 'SA_NATIONAL', status: 'ACTIVE',
    changeVersion: 1, organizationTypes: [], identifierTypes: [], relationTypes: [],
    rules: [{ scopeType: 'JURISDICTION', scopeCode: 'SA', ruleCode: 'UBO_THRESHOLD_PCT', ruleValue: '25', status: 'ACTIVE' },
      { scopeType: 'JURISDICTION', scopeCode: 'SA', ruleCode: 'ACCEPTANCE_POLICY', ruleValue: 'OPEN', status: 'ACTIVE' }] };
  const list = decodeJurisdictions([sa]);
  assert.equal(list[0].ruleSettings[0].ruleValue, '25');
  assert.equal(list[0].acceptancePolicy, 'OPEN', 'acceptance policy comes from the jurisdiction rule');
  assert.equal(decodeJurisdictions([{ ...sa, rules: [] }])[0].acceptancePolicy, 'OPEN', 'no rule means the built-in default OPEN');
  assert.throws(() => decodeJurisdictions([sa, sa]));
});

test('schemas belong to the selected jurisdiction; fields group by section then group in order', () => {
  const schemas = decodeSchemas([{ schemaId: '6101', jurisdictionCode: 'SA', partyType: 'ORGANIZATION', roleType: 'CLIENT', organizationTypeCode: 'LLC',
    versionNo: 1, status: 'ACTIVE', fields: [field('crNumber', 'BASIC', 'registration', 2), field('legalName', 'BASIC', 'identity', 1),
      field('industry', 'BUSINESS', null, 3, 'INDUSTRY'), field('buildingNumber', 'BASIC', 'identity', 4)] }], 'SA');
  assert.deepEqual(schemas[0].fields.map(f => f.fieldKey), ['legalName', 'crNumber', 'industry', 'buildingNumber']);
  assert.deepEqual(groupFields(schemas[0].fields).map(s => [s.section, s.groups.map(g => [g.group, g.fields.length])]),
    [['BASIC', [['identity', 2], ['registration', 1]]], ['BUSINESS', [['', 1]]]]);
  assert.deepEqual(referencedCatalogs(schemas), ['INDUSTRY']);
  assert.throws(() => decodeSchemas([{ schemaId: '6101', jurisdictionCode: 'AE', partyType: 'ORGANIZATION', versionNo: 1, status: 'ACTIVE', fields: [] }], 'SA'));
});

test('catalog items match the requested catalog and sort by display order', () => {
  const items = decodeCatalogItems([{ catalogCode: 'INDUSTRY', itemCode: 'RETAIL', jurisdictionCode: '*', labelKey: null, clientTypes: null, displayOrder: 2, status: 'ACTIVE' },
    { catalogCode: 'INDUSTRY', itemCode: 'GAMES', jurisdictionCode: 'SA', labelKey: null, clientTypes: 'ORGANIZATION', displayOrder: 1, status: 'ACTIVE' }], 'INDUSTRY');
  assert.deepEqual(items.map(i => i.itemCode), ['GAMES', 'RETAIL']);
  assert.equal(items[1].jurisdictionCode, null, '* means global');
  assert.throws(() => decodeCatalogItems([{ ...items[0], catalogCode: 'MCC' }], 'INDUSTRY'));
});

test('programs: jurisdiction or global only; materials, checks and screenings flatten into one list', () => {
  const program = { programId: '5001', programCode: 'SA_ORG_STD', programVersionId: '5101', versionNo: 2, qualificationType: 'ORGANIZATION_KYB', jurisdictionCode: 'SA',
    tier: 'STANDARD', ownerPartnerId: null, reviewIntervalMonths: 12, fourEyesRequired: true, partyType: 'ORGANIZATION', roleType: 'CLIENT',
    programStatus: 'ACTIVE', versionStatus: 'ACTIVE', approvedBy: null, approvedTime: null,
    materials: [{ requirementCode: 'COMMERCIAL_REGISTRATION', subjectRole: 'PRIMARY', documentTypes: ['CR_CERTIFICATE'], required: true, maxAgeDays: 90, waivable: false }],
    checks: [{ checkCode: 'REGISTRY_REVIEW', checkType: 'REGISTRY', checkMode: 'MANUAL', subjectRole: 'PRIMARY', blocking: true, validityDays: null }],
    screenings: [{ screeningType: 'SANCTIONS', subjectRole: 'ANY_RELATED', required: true, monitoringRequired: false }] };
  const [decoded, global] = decodePrograms({ items: [program, { ...program, programVersionId: '5102', jurisdictionCode: null, versionStatus: 'RETIRED' }] }, 'SA');
  assert.deepEqual(decoded.items.map(i => [i.kind, i.code, i.required]), [['MATERIAL', 'COMMERCIAL_REGISTRATION', true], ['CHECK', 'REGISTRY_REVIEW', true], ['SCREENING', 'SANCTIONS', true]]);
  assert.deepEqual(decoded.items[0].detail, ['CR_CERTIFICATE', 'maxAge:90']);
  assert.equal(programInUse(decoded), true);
  assert.equal(programInUse(global), false, 'a RETIRED version is listed but not used');
  assert.throws(() => decodePrograms({ items: [{ ...program, jurisdictionCode: 'AE' }] }, 'SA'));
});

test('real AE schema list: organizationTypeCode * is the default schema for all organization types', () => {
  // 与 mor-test AE 响应同形：机构默认 Schema（*）、FZ_LLC 专属 Schema、个人默认 Schema
  const schema = (schemaId: string, partyType: string, organizationTypeCode: string) => ({ schemaId, jurisdictionCode: 'AE', partyType, roleType: 'CLIENT',
    organizationTypeCode, versionNo: 1, status: 'ACTIVE', approvedTime: '2026-10-27T00:00:00.000000Z', fields: [field('organization.legalName', 'BASIC', 'IDENTITY', 1)] });
  const decoded = decodeSchemas([schema('9270000000700001', 'ORGANIZATION', '*'), schema('9270000000700002', 'ORGANIZATION', 'FZ_LLC'),
    schema('9270000000700003', 'INDIVIDUAL', '*')], 'AE');
  assert.deepEqual(decoded.map(s => s.organizationTypeCode), [null, 'FZ_LLC', null]);
  assert.throws(() => decodeSchemas([schema('9270000000700009', 'ORGANIZATION', '**')], 'AE'), 'only the exact wildcard is accepted');
});

test('jurisdictions decode optional address-format components; absent means none', () => {
  const ae = { jurisdictionCode: 'AE', countryCode: 'AE', labelKey: null, addressFormatCode: 'AE_POBOX', status: 'ACTIVE', changeVersion: 1, rules: [],
    organizationTypes: [], identifierTypes: [], relationTypes: [] };
  assert.deepEqual(decodeJurisdictions([ae])[0].addressComponents, []);
  assert.deepEqual(decodeJurisdictions([{ ...ae, addressComponents: [{ component: 'stateOrProvince', catalogCode: 'REF_COUNTRY_SUBDIVISION', required: true, order: 3 }] }])[0]
    .addressComponents, [{ component: 'stateOrProvince', catalogCode: 'REF_COUNTRY_SUBDIVISION' }]);
  assert.throws(() => decodeJurisdictions([{ ...ae, addressComponents: [{ component: 'stateOrProvince', catalogCode: 'ref_x' }] }]));
});

test('catalog sources: REF_ catalogs come from Platform reference data and link to their maintenance page; others are onboarding catalogs', () => {
  const field = (fieldKey: string, catalogCode: string | null) => ({ section: 'BUSINESS', group: null, fieldKey, widget: 'SELECT', catalogCode, requirementLevel: 'REQUIRED',
    requirementGroup: null, appliesTo: null, visibleWhen: null, labelKey: null, order: 1 });
  const schema = (fields: ReturnType<typeof field>[]) => ({ schemaId: '1', jurisdictionCode: 'AE', partyType: 'ORGANIZATION', roleType: 'CLIENT',
    organizationTypeCode: null, versionNo: 1, status: 'ACTIVE', fields });
  const usages = catalogUsages([schema([field('business.businessModelCode', 'BUSINESS_MODEL'), field('business.currency', 'REF_CURRENCY'), field('organization.legalName', null)]),
    schema([field('address.stateOrProvince', 'REF_COUNTRY_SUBDIVISION'), field('business.currency', 'REF_CURRENCY')])]);
  assert.deepEqual(usages, [
    { catalogCode: 'BUSINESS_MODEL', source: 'ONBOARDING', fieldKeys: ['business.businessModelCode'] },
    { catalogCode: 'REF_CURRENCY', source: 'PLATFORM', fieldKeys: ['business.currency'] },
    { catalogCode: 'REF_COUNTRY_SUBDIVISION', source: 'PLATFORM', fieldKeys: ['address.stateOrProvince'] },
  ]);
  assert.equal(catalogSource('REF_ANYTHING'), 'PLATFORM');
  const withAddress = catalogUsages([schema([field('business.currency', 'REF_CURRENCY')])], { addressFormatCode: 'AE_POBOX',
    addressComponents: [{ component: 'line1', catalogCode: null }, { component: 'stateOrProvince', catalogCode: 'REF_COUNTRY_SUBDIVISION' }] });
  assert.deepEqual(withAddress.map(u => [u.catalogCode, u.fieldKeys]), [['REF_CURRENCY', ['business.currency']],
    ['REF_COUNTRY_SUBDIVISION', ['address.stateOrProvince (AE_POBOX)']]], 'address-format components list their catalogs too (prototype 09)');
  assert.equal(referenceCatalogHref('REF_COUNTRY_SUBDIVISION', 'AE'), '/reference-data?tab=subdivisions&country=AE');
  assert.equal(referenceCatalogHref('REF_CURRENCY', 'AE'), '/reference-data?tab=currencies');
  assert.equal(referenceCatalogHref('BUSINESS_MODEL', 'AE'), null);
});
