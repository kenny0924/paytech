import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { enumLabel } from '../i18n/index.ts';
import { testT } from '../i18n/testing.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  changeLogoBody, createCurrencySpecBody, currencySpecPath, currencyStatusChange, currencyStatusCounts, decodeCurrencySpecList,
  decodeCurrencySpecMutation, editableCurrencySpec, logoChangeBlocked, manageCurrencySpecs, updateCurrencySpecBody,
} from './currencySpecModel.ts';
import type { CurrencySpec } from './currencySpecModel.ts';

const t = testT('currency');

const usd: CurrencySpec = { currency: 'USD', numericCode: '840', name: 'US Dollar', symbol: '$', minorUnit: 2, roundingMode: 'HALF_UP',
  logoFileAddress: '/files/v1/7312345678901234001', logoPublicUrl: 'https://static.local.test/usd.svg', status: 'ACTIVE', version: 2 };
const aed: CurrencySpec = { ...usd, currency: 'AED', numericCode: '784', name: 'UAE Dirham', symbol: 'AED', logoFileAddress: null, logoPublicUrl: null,
  status: 'DRAFT', version: 1 };
const jpy: CurrencySpec = { ...usd, currency: 'JPY', numericCode: null, name: null, symbol: null, minorUnit: 0, status: 'INACTIVE', version: 3 };
const mutation = { objectType: 'CURRENCY_SPEC', objectId: '7312345678901234567', objectCode: 'AED', status: 'DRAFT', version: 1 };

test('页面注册：币种页挂在 /currency-specs，管理按钮与 Platform 能力清单一致', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_CURRENCY_SPECS');
  assert.equal(page?.frontendRoute, '/currency-specs');
  assert.deepEqual(page?.actions.map(action => action.resourceCode), [manageCurrencySpecs]);
  assert.equal(currencySpecPath, '/currency-admin/v1/currency-specs');
});

test('列表解码：字段集合、代码、小数位、枚举、版本、数字代码与图标地址严格校验，未知值与重复代码失败关闭', () => {
  assert.deepEqual(decodeCurrencySpecList({ items: [aed, jpy, usd] }).map(item => item.currency), ['AED', 'JPY', 'USD']);
  assert.deepEqual(decodeCurrencySpecList({ items: [] }), []);
  for (const invalid of [
    { ...usd, currency: 'usd' }, { ...usd, minorUnit: 9 }, { ...usd, minorUnit: 2.5 }, { ...usd, minorUnit: '2' },
    { ...usd, roundingMode: 'HALF_EVEN' }, { ...usd, status: 'ENABLED' }, { ...usd, version: 0 }, { ...usd, numericCode: '84' },
    { ...usd, name: 'x'.repeat(129) }, { ...usd, symbol: 'x'.repeat(9) }, { ...usd, logoFileAddress: 'https://static.local.test/usd.svg' },
    { ...usd, logoPublicUrl: 'javascript:alert(1)' }, { ...usd, internalNote: 'x' },
    { currency: 'USD', minorUnit: 2, roundingMode: 'HALF_UP', status: 'ACTIVE', version: 2 },
  ]) {
    assert.throws(() => decodeCurrencySpecList({ items: [invalid] }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeCurrencySpecList({ items: [usd, usd] }), ApiError);
  assert.throws(() => decodeCurrencySpecList([usd]), ApiError);
  assert.throws(() => decodeCurrencySpecList({ items: [usd], total: 1 }), ApiError);
});

test('写回执解码：类型、主键、币种、目标状态与版本必须对应本次操作', () => {
  assert.equal(decodeCurrencySpecMutation(mutation, 'AED', 'DRAFT').version, 1);
  assert.throws(() => decodeCurrencySpecMutation({ ...mutation, objectType: 'PERMISSION' }, 'AED', 'DRAFT'), ApiError);
  assert.throws(() => decodeCurrencySpecMutation({ ...mutation, objectId: '0' }, 'AED', 'DRAFT'), ApiError);
  assert.throws(() => decodeCurrencySpecMutation(mutation, 'USD', 'DRAFT'), ApiError);
  assert.throws(() => decodeCurrencySpecMutation(mutation, 'AED', 'ACTIVE'), ApiError);
  assert.throws(() => decodeCurrencySpecMutation({ ...mutation, version: 0 }, 'AED', 'DRAFT'), ApiError);
  assert.equal(decodeCurrencySpecMutation({ ...mutation, status: 'ACTIVE' }, 'AED', null).status, 'ACTIVE', '修改不改变状态：任一已知状态');
  assert.throws(() => decodeCurrencySpecMutation({ ...mutation, status: 'ENABLED' }, 'AED', null), ApiError);
});

test('请求体：新建去除空白并只发合同字段；修改带原版本、不带币种代码；换图标按当前值提交标准属性', () => {
  assert.deepEqual(createCurrencySpecBody({ currency: ' AED ', numericCode: ' 784 ', name: ' UAE Dirham ', symbol: ' AED ', minorUnit: 2,
    roundingMode: 'HALF_UP', reason: ' 新增 ' }),
  { currency: 'AED', numericCode: '784', name: 'UAE Dirham', symbol: 'AED', logoFileAddress: null, minorUnit: 2, roundingMode: 'HALF_UP', reason: '新增' });
  const update = updateCurrencySpecBody({ currency: 'XXX', numericCode: '784', name: 'UAE Dirham', symbol: 'AED',
    logoFileAddress: '/files/v1/9', minorUnit: 3, roundingMode: 'HALF_UP', reason: ' 修正 ' }, aed);
  assert.deepEqual(update, { numericCode: '784', name: 'UAE Dirham', symbol: 'AED', logoFileAddress: '/files/v1/9', minorUnit: 3,
    roundingMode: 'HALF_UP', expectedVersion: 1, reason: '修正' });
  assert.equal('currency' in update, false);
  assert.deepEqual(changeLogoBody(usd, '/files/v1/7312345678901234002', ' 统一图标 '), { numericCode: '840', name: 'US Dollar', symbol: '$',
    logoFileAddress: '/files/v1/7312345678901234002', minorUnit: 2, roundingMode: 'HALF_UP', expectedVersion: 2, reason: '统一图标' });
});

test('筛选计数与换图标前提：标准属性未补齐的已启用币种不能只换图标', () => {
  assert.deepEqual(currencyStatusCounts([aed, usd, jpy]), { ALL: 3, DRAFT: 1, ACTIVE: 1, INACTIVE: 1 });
  assert.deepEqual([aed, usd, jpy].map(logoChangeBlocked), [false, false, true]);
});

test('只有草稿可编辑；草稿与停用可启用，启用可停用，确认文案说明冻结与停用影响（以英文基准断言）', () => {
  assert.deepEqual([aed, usd, jpy].map(editableCurrencySpec), [true, false, false]);
  assert.deepEqual([aed, usd, jpy].map(spec => [currencyStatusChange(spec).target, currencyStatusChange(spec).verb]),
    [['ACTIVE', 'Enable'], ['INACTIVE', 'Disable'], ['ACTIVE', 'Enable']]);
  const activate = currencyStatusChange(aed).notice;
  assert.equal(activate, t('statusChange.enableNotice', { minorUnit: 2, roundingMode: 'Round half up (HALF_UP)' }));
  assert.match(activate, /^Check again: 2 decimal places, rounding mode Round half up \(HALF_UP\)\./);
  assert.match(currencyStatusChange({ ...aed, minorUnit: 1 }).notice, /: 1 decimal place,/);
  assert.match(currencyStatusChange(jpy).notice, /: 0 decimal places,/);
  assert.equal(currencyStatusChange(usd).notice, t('statusChange.disableNotice'));
});

test('枚举显示名：已知值取 currency.enum.*，未知值原样显示', () => {
  assert.equal(enumLabel(t, 'enum.status', 'ACTIVE'), 'Enabled');
  assert.equal(enumLabel(t, 'enum.roundingMode', 'HALF_UP'), 'Round half up (HALF_UP)');
  assert.equal(enumLabel(t, 'enum.status', 'ARCHIVED'), 'ARCHIVED');
  assert.equal(enumLabel(t, 'enum.status', null), '');
});
