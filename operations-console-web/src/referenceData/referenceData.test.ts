import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { enumLabel } from '../i18n/index.ts';
import { testT } from '../i18n/testing.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  countryName, currencyName, decodeCountries, industryDescription, decodeIndustries, decodeIndustryCategory, decodeMccPage, decodeSubdivisions, industryTree,
  matchesKeyword, mccQuery, mccUsage, refdataName, referenceDataPaths, referenceDataRoute, shortFileAddress, subdivisionCountries, untranslated,
} from './referenceData.ts';
import type { RefIndustry } from './referenceData.ts';

const t = testT('reference-data');
/** 测试中没有发布的 refdata 语言包：refdataName 回退后端 name，再回退编码。 */
const tRef = testT('reference-data');

const ae = { countryCode: 'AE', alpha3Code: 'ARE', numericCode: '784', name: 'United Arab Emirates', regionCode: 'GCC', callingCode: '+971',
  defaultCurrencyCode: 'AED', logoFileAddress: '/files/v1/7312345678901234101', logoPublicUrl: 'https://static.local.test/ae.svg', status: 'ACTIVE', version: 1 };
const aq = { countryCode: 'AQ', alpha3Code: 'ATA', numericCode: '010', name: 'Antarctica', regionCode: 'APAC', callingCode: null, defaultCurrencyCode: null,
  logoFileAddress: null, logoPublicUrl: null, status: 'ACTIVE', version: 1 };
const industry = (industryCode: string, level: 1 | 2, parentIndustryCode: string | null, sortNo: number, defaultMccCode: string | null = null,
  status = 'ACTIVE'): RefIndustry => ({ industryCode, parentIndustryCode, level, name: industryCode.toLowerCase(), description: null, defaultMccCode,
  logoFileAddress: null, logoPublicUrl: null, sortNo, status: status as RefIndustry['status'], version: 1 });

test('page registration: the reference data page sits at /reference-data under the Platform registry, read-only (no actions)', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_REFERENCE_DATA');
  assert.equal(page?.frontendRoute, referenceDataRoute);
  assert.deepEqual(page?.actions, []);
  assert.deepEqual(Object.values(referenceDataPaths), ['/reference-data-admin/v1/countries', '/reference-data-admin/v1/country-subdivisions',
    '/reference-data-admin/v1/industries', '/reference-data-admin/v1/mccs']);
});

test('countries decode: optional standard attributes may be null; unknown status, bad codes, non-file logo and duplicates fail closed', () => {
  assert.deepEqual(decodeCountries({ items: [ae, aq] }).map(c => [c.countryCode, c.callingCode]), [['AE', '+971'], ['AQ', null]]);
  assert.equal(decodeCountries({ items: [{ countryCode: 'AE', status: 'ACTIVE', version: 1 }] })[0].alpha3Code, null, 'absent fields become null');
  for (const invalid of [{ ...ae, countryCode: 'ARE' }, { ...ae, status: 'ENABLED' }, { ...ae, version: 0 }, { ...ae, logoFileAddress: 'https://x/ae.svg' },
    { ...ae, logoPublicUrl: 'javascript:alert(1)' }, { ...ae, callingCode: '+9711234567' }]) {
    assert.throws(() => decodeCountries({ items: [invalid] }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeCountries({ items: [ae, ae] }), ApiError);
  assert.throws(() => decodeCountries([ae]), ApiError);
});

test('subdivisions decode: ISO 3166-2 hyphen codes that start with their country; an optional country filter must hold', () => {
  const du = { subdivisionCode: 'AE-DU', countryCode: 'AE', parentSubdivisionCode: null, category: 'EMIRATE', name: 'Dubai', localName: 'دبي', status: 'ACTIVE', version: 1 };
  assert.equal(decodeSubdivisions({ items: [du] }, 'AE')[0].localName, 'دبي');
  assert.throws(() => decodeSubdivisions({ items: [{ ...du, subdivisionCode: 'AE_DU' }] }), ApiError);
  assert.throws(() => decodeSubdivisions({ items: [{ ...du, subdivisionCode: 'SA-01' }] }), ApiError);
  assert.throws(() => decodeSubdivisions({ items: [du] }, 'SA'), ApiError);
  assert.deepEqual(subdivisionCountries(decodeSubdivisions({ items: [du, { ...du, subdivisionCode: 'EG-C', countryCode: 'EG' }, { ...du, subdivisionCode: 'AE-AZ' }] })),
    [{ countryCode: 'AE', count: 2 }, { countryCode: 'EG', count: 1 }]);
});

test('industries decode and tree: categories by sort order, each followed by its subcategories; orphans grouped last', () => {
  const items = [industry('RETAIL_HOME', 2, 'RETAIL', 30, '5712'), industry('RETAIL', 1, null, 20), industry('DIGITAL_GOODS', 1, null, 10),
    industry('RETAIL_APPAREL', 2, 'RETAIL', 10, '5651'), industry('OTHER_RETAIL', 2, 'OTHER', 10, '5999')];
  const decoded = decodeIndustries({ items });
  const tree = industryTree(decoded);
  assert.deepEqual(tree.map(group => [group.category?.industryCode ?? null, group.children.map(c => c.industryCode)]),
    [['DIGITAL_GOODS', []], ['RETAIL', ['RETAIL_APPAREL', 'RETAIL_HOME']], [null, ['OTHER_RETAIL']]]);
  assert.throws(() => decodeIndustries({ items: [industry('RETAIL', 1, 'OTHER', 1)] }), ApiError, 'a category has no parent');
  assert.throws(() => decodeIndustries({ items: [{ ...industry('RETAIL_HOME', 2, 'RETAIL', 1), parentIndustryCode: null }] }), ApiError);
  assert.throws(() => decodeIndustries({ items: [industry('RETAIL_HOME', 2, 'RETAIL', 1, '57A2')] }), ApiError);
  assert.deepEqual([...mccUsage(decoded)], [['5712', ['RETAIL_HOME']], ['5651', ['RETAIL_APPAREL']], ['5999', ['OTHER_RETAIL']]]);
  assert.throws(() => decodeIndustryCategory({ industryCode: 'RETAIL', name: 'Retail', logoPublicUrl: null,
    children: [industry('DIGITAL_GAMES', 2, 'DIGITAL_GOODS', 10)] }), ApiError, 'children belong to the category');
});

test('MCC page decode: page window, unique codes, boolean high-risk flag', () => {
  const mcc = { mccCode: '5816', name: 'Digital Goods - Games', description: null, networkHighRisk: false, status: 'ACTIVE', version: 1 };
  assert.equal(decodeMccPage({ items: [mcc], page: 1, size: 10, total: 1 }).items[0].mccCode, '5816');
  assert.throws(() => decodeMccPage({ items: [{ ...mcc, mccCode: '581' }], page: 1, size: 10, total: 1 }), ApiError);
  assert.throws(() => decodeMccPage({ items: [{ ...mcc, networkHighRisk: 'false' }], page: 1, size: 10, total: 1 }), ApiError);
  assert.throws(() => decodeMccPage({ items: [mcc, mcc], page: 1, size: 10, total: 2 }), ApiError);
  assert.deepEqual(mccQuery('  software ', 'ACTIVE'), { keyword: 'software', status: 'ACTIVE' });
  assert.deepEqual(mccQuery('', 'ALL'), {});
});

test('display names: Intl for countries and currencies, refdata text then backend name then code for the rest (B7, AC-14)', () => {
  assert.equal(countryName('AE', 'United Arab Emirates', 'en'), 'United Arab Emirates');
  assert.equal(countryName('AE', null, 'zh-CN'), '阿拉伯联合酋长国');
  assert.equal(countryName('XK', 'Kosovo', 'en'), 'Kosovo');
  assert.equal(currencyName('AED', 'UAE Dirham', 'en'), 'United Arab Emirates Dirham');
  assert.equal(currencyName('XTS', 'Testing Code', 'en') !== 'XTS', true);
  assert.equal(currencyName('ZZZ', 'Synthetic', 'en'), 'Synthetic');
  assert.equal(refdataName(tRef, 'industry', 'RETAIL_APPAREL', 'Apparel & accessories'), 'Apparel & accessories', 'no refdata bundle: backend name');
  assert.equal(refdataName(tRef, 'mcc', '5816', null), '5816', 'no name either: the code, never a raw key');
  assert.equal(untranslated(tRef, 'mcc', '5816', 'Digital Goods - Games', 'zh-CN'), true);
  assert.equal(untranslated(tRef, 'mcc', '5816', 'Digital Goods - Games', 'en'), false);
  assert.equal(industryDescription(tRef, 'RETAIL_HOME', 'Furniture, home decor'), 'Furniture, home decor', 'no refdata description key: backend text');
  assert.equal(industryDescription(tRef, 'RETAIL_HOME', null), null);
});

test('search and helpers', () => {
  assert.equal(matchesKeyword('are', 'AE', 'ARE', '784', 'United Arab Emirates'), true);
  assert.equal(matchesKeyword(' emirates ', 'AE', 'ARE', null, 'United Arab Emirates'), true);
  assert.equal(matchesKeyword('xyz', 'AE', 'ARE'), false);
  assert.equal(matchesKeyword('', 'AE'), true);
  assert.equal(shortFileAddress('/files/v1/7312345678901234101'), '/files/v1/7312…01');
  assert.equal(shortFileAddress('/files/v1/12'), '/files/v1/12');
});

test('labels: statuses, levels and subdivision categories use the reference-data enum keys; unknown values stay raw', () => {
  assert.equal(enumLabel(t, 'enum.status', 'ACTIVE'), 'Active');
  assert.equal(enumLabel(t, 'enum.industryLevel', '2'), 'Subcategory');
  assert.equal(enumLabel(t, 'enum.subdivisionCategory', 'GOVERNORATE'), 'Governorate (GOVERNORATE)');
  assert.equal(enumLabel(t, 'enum.subdivisionCategory', 'PROVINCE'), 'PROVINCE');
  assert.equal(t('country.summary', { count: 249 }), '249 countries · sorted by name');
});
