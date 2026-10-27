import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeCatalogItems, decodeJurisdictions, decodePrograms, decodeSchemas, groupFields, programInUse, referencedCatalogs } from './onboardingConfig.ts';

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
