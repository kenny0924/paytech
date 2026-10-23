import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { decodeAgreement } from './commercial.ts';
import type { AgreementCapability } from './commercial.ts';
import {
  addConditionValue, capabilityBody, capabilityChoices, capabilityDimensions, capabilityFormPatch, conditionControl, conditionForm, conditionIssues,
  conditionOptions, conditionSummary, conditionTags, conditionValueIssue, conditionValueLabel, conditionsBody, currencyCodes, currencySelectOptions,
  conditionTagValue, decodeDimensions, dimensionLabel, dimensionSuggestions, dimensionNames, existingMark, mergeCurrencies, normalizeValues, orderedDimensions, require3dsTag,
  splitCurrencies, reservePercent, reservePercentIssue, reserveText, toggleConditionValue, unrestricted,
} from './capabilityConditions.ts';
import type { DimensionDefinition } from './capabilityConditions.ts';
import type { CapabilityDimension, CatalogCapability } from './commercialConsole.ts';

const cardDimensions: CapabilityDimension[] = [{ dimensionCode: 'CURRENCY', required: true }, { dimensionCode: 'CONSUMER_COUNTRY', required: false },
  { dimensionCode: 'CARD_BRAND', required: false }, { dimensionCode: 'FUNDING_TYPE', required: false }];
const definitions: DimensionDefinition[] = [{ dimensionCode: 'CARD_BRAND', displayName: '卡品牌', factSource: 'CARD_BIN', evaluationPoint: 'CARD_ENTRY',
  acceptanceAllowed: true, pricingAllowed: true }, { dimensionCode: 'NEW_DIM', displayName: '新维度', factSource: 'ORDER', evaluationPoint: 'CHECKOUT',
  acceptanceAllowed: true, pricingAllowed: true }];
const catalogItem = (capabilityCode: string, productCode = 'ACQUIRING', status = 'ACTIVE'): CatalogCapability => ({ productCapabilityId: '9100000000000002001',
  productCode, capabilityCode, capabilityName: capabilityCode, capabilityKind: 'PAYMENT_METHOD', paymentMethodCode: capabilityCode, dimensionCode: null,
  dimensionValue: null, status, dimensions: cardDimensions, changeVersion: 1 });
const capability = (extra: Partial<AgreementCapability> = {}): AgreementCapability => ({ agreementCapabilityId: '7319000000000000301', entityNo: 'AE001',
  capabilityCode: 'CARD', status: 'ENABLED', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, rollingReserveRate: '0.05', rollingReserveDays: 90,
  conditions: [{ dimensionCode: 'CARD_BRAND', values: ['VISA', 'MASTERCARD'] }, { dimensionCode: 'FUNDING_TYPE', values: ['DEBIT'] },
    { dimensionCode: 'CURRENCY', values: ['AED', 'USD'] }], require3ds: true, ...extra });

test('AC-03 条件 ↔ 请求体：按可配维度顺序整理、转大写去重、空维度不提交，读回一致', () => {
  const form = { CARD_BRAND: [' visa', 'MASTERCARD', 'VISA'], FUNDING_TYPE: ['debit'], CURRENCY: ['aed', 'USD'], CONSUMER_COUNTRY: [], ISSUER_COUNTRY: ['AE'] };
  const body = conditionsBody(cardDimensions, form);
  assert.deepEqual(body, [{ dimensionCode: 'CURRENCY', values: ['AED', 'USD'] }, { dimensionCode: 'CARD_BRAND', values: ['VISA', 'MASTERCARD'] },
    { dimensionCode: 'FUNDING_TYPE', values: ['DEBIT'] }], '不可配的 ISSUER_COUNTRY 不提交，空的 CONSUMER_COUNTRY 不提交');
  assert.deepEqual(conditionsBody(cardDimensions, conditionForm(body)), body, '读回再提交不变');
  assert.deepEqual(normalizeValues([' a ', 'A', '', 'b']), ['A', 'B']);
});

test('AC-03 条件前置检查：CURRENCY 必填、按维度校验取值格式', () => {
  assert.deepEqual(conditionIssues(cardDimensions, { CARD_BRAND: ['VISA'] }, definitions), ['Currency is required']);
  assert.deepEqual(conditionIssues(cardDimensions, { CURRENCY: ['US'], CONSUMER_COUNTRY: ['ARE'], CARD_BRAND: ['VISA-1'] }, definitions), [
    "Currency: US isn't a 3-letter currency code", "Consumer Country: ARE isn't a 2-letter country code",
    'Card Brand: VISA-1 can contain only uppercase letters, digits and underscores']);
  assert.deepEqual(conditionIssues([{ dimensionCode: 'CURRENCY_PAIR', required: false }], { CURRENCY_PAIR: ['USDAED'] }),
    ["Currency Pair: USDAED isn't a currency pair (e.g. USD/AED)"]);
  assert.deepEqual(conditionIssues(cardDimensions, { CURRENCY: ['AED'] }), []);
  assert.equal(conditionValueIssue('CURRENCY_PAIR', 'USD/AED'), null);
  assert.equal(conditionValueIssue('DESTINATION_COUNTRY', 'SA'), null);
  assert.deepEqual(['CURRENCY', 'CONSUMER_COUNTRY', 'ISSUER_COUNTRY', 'DESTINATION_COUNTRY', 'CARD_BRAND', 'FUNDING_TYPE', 'CARD_LEVEL', 'CARD_PRODUCT',
    'ISSUER_REGION', 'CURRENCY_PAIR', 'PAYOUT_RAIL'].map(conditionControl),
  ['CURRENCY', 'COUNTRY', 'COUNTRY', 'COUNTRY', 'ENUM', 'ENUM', 'ENUM', 'ENUM', 'ENUM', 'TAGS', 'TAGS']);
});

test('保证金按百分比输入：5 → 0.05，读回 0.05 → 5；超出 0–100 提示', () => {
  const values = { entityNo: ' AE001 ', capabilityCode: 'CARD', effectiveFrom: '2026-11-01T00:00:00Z', reservePercent: '5', rollingReserveDays: 90,
    conditions: { CURRENCY: ['aed'], CARD_BRAND: ['visa'] }, require3ds: true };
  const created = capabilityBody(6, values, cardDimensions, false);
  assert.equal(created.rollingReserveRate, '0.05');
  assert.equal(created.require3ds, true);
  assert.deepEqual(created.conditions, [{ dimensionCode: 'CURRENCY', values: ['AED'] }, { dimensionCode: 'CARD_BRAND', values: ['VISA'] }]);
  assert.deepEqual([(created as { entityNo?: string }).entityNo, Object.hasOwn(created, 'status'), Object.hasOwn(created, 'limits')], ['AE001', false, false]);
  const updated = capabilityBody(7, { ...values, status: 'SUSPENDED', reservePercent: ' ', rollingReserveDays: null, require3ds: undefined }, cardDimensions, true);
  assert.deepEqual([(updated as { status?: string }).status, Object.hasOwn(updated, 'entityNo'), updated.rollingReserveRate, updated.require3ds],
    ['SUSPENDED', false, null, false]);
  assert.equal(reservePercent('0.05'), '5');
  assert.equal(reservePercent(null), undefined);
  assert.equal(reservePercentIssue('101'), 'Enter a percentage from 0 to 100 with up to 8 decimal places.');
  assert.equal(reservePercentIssue('12.5'), null);
  assert.equal(reserveText(capability()), '5% / 90 days');
  assert.equal(reserveText(capability({ rollingReserveRate: null })), '—');
  assert.deepEqual(capabilityFormPatch(capability()).conditions, { CARD_BRAND: ['VISA', 'MASTERCARD'], FUNDING_TYPE: ['DEBIT'], CURRENCY: ['AED', 'USD'] });
});

test('O3 列表接受条件列：取值同抽屉（枚举中文、国家「代码 中文」、币种代码）+「要求 3DS」；维度名优先取维度清单', () => {
  assert.deepEqual(conditionTags(capability(), definitions), ['Card Brand VISA / MASTERCARD', 'Funding Type Debit', 'Currency AED / USD', '3DS Required']);
  assert.equal(require3dsTag(), '3DS Required');
  assert.deepEqual(conditionTags(capability({ conditions: [{ dimensionCode: 'CONSUMER_COUNTRY', values: ['AE', 'ZZ'] },
    { dimensionCode: 'ISSUER_REGION', values: ['GCC'] }], require3ds: false })), ['Consumer Country AE United Arab Emirates / ZZ', 'Issuer Region GCC']);
  assert.deepEqual([conditionTagValue('ISSUER_COUNTRY', 'SA'), conditionTagValue('FUNDING_TYPE', 'CREDIT'), conditionTagValue('CURRENCY', 'AED'),
    conditionTagValue('CARD_BRAND', 'VISA')], ['SA Saudi Arabia', 'Credit', 'AED', 'VISA']);
  assert.deepEqual(conditionTags(capability({ conditions: [], require3ds: false })), []);
  assert.equal(dimensionLabel('ISSUER_REGION'), 'Issuer Region');
  assert.equal(dimensionLabel('NEW_DIM', definitions), '新维度', '界面未登记的维度用维度清单名称');
  assert.equal(dimensionLabel('NEW_DIM'), 'NEW_DIM');
  assert.equal(dimensionLabel('UNKNOWN_DIM'), 'UNKNOWN_DIM');
});

test('F1 可开通卡片：已订阅产品下 ACTIVE 的项全部列出，该 OE 已有的项带上那条开通项（置灰、点击编辑）；可配维度目录缺失时按已有条件推出', () => {
  const catalog = [catalogItem('CARD'), catalogItem('APPLE_PAY'), catalogItem('STC_PAY', 'ACQUIRING', 'RETIRED'), catalogItem('PAYOUT_LOCAL', 'PAYOUT')];
  const card = capability();
  const suspended = capability({ agreementCapabilityId: '7319000000000000302', capabilityCode: 'APPLE_PAY', status: 'SUSPENDED' });
  const choices = capabilityChoices(catalog, ['ACQUIRING'], [card, suspended, capability({ entityNo: 'SA001', capabilityCode: 'PAYOUT_LOCAL' })], 'AE001');
  assert.deepEqual(choices.map(choice => [choice.item.capabilityCode, choice.existing?.agreementCapabilityId ?? null]),
    [['CARD', card.agreementCapabilityId], ['APPLE_PAY', suspended.agreementCapabilityId]], '已开通的 CARD 仍显示；停用目录项与未订阅产品不列');
  assert.deepEqual(capabilityChoices(catalog, ['ACQUIRING', 'PAYOUT'], [card], 'SA001').map(choice => [choice.item.capabilityCode, !!choice.existing]),
    [['CARD', false], ['APPLE_PAY', false], ['PAYOUT_LOCAL', false]], '其他 OE 的开通项不影响本 OE');
  assert.deepEqual([existingMark(card), existingMark(suspended), existingMark(capability({ status: 'DISABLED' })), existingMark(null)],
    ['Enabled', 'Added · Suspended', 'Added · Disabled', null]);
  assert.equal(dimensionNames(cardDimensions, definitions), 'Configurable: Currency, Consumer Country, Card Brand, Funding Type');
  assert.equal(dimensionNames([]), 'No configurable dimensions');
  assert.equal(capabilityDimensions(catalogItem('CARD')), cardDimensions);
  assert.deepEqual(capabilityDimensions(catalogItem('CARD'), capability().conditions).map(d => d.dimensionCode),
    ['CURRENCY', 'CONSUMER_COUNTRY', 'CARD_BRAND', 'FUNDING_TYPE'], '目录可配的维度不重复追加');
  assert.deepEqual(capabilityDimensions(catalogItem('CARD'), [{ dimensionCode: 'CARD_LEVEL', values: ['GOLD'] }]).map(d => [d.dimensionCode, d.required]).at(-1),
    ['CARD_LEVEL', false], '已有条件的维度不在目录可配中时追加，编辑回填不丢');
  assert.deepEqual(capabilityDimensions(undefined, capability().conditions).map(d => [d.dimensionCode, d.required]),
    [['CARD_BRAND', false], ['FUNDING_TYPE', false], ['CURRENCY', true]]);
});

test('F2 不限切换与取值：点选追加 / 取消，「不限」即空；其他取值追加去重；列表外的已选值保留在平铺选项里', () => {
  assert.equal(unrestricted([]), true);
  assert.equal(unrestricted(undefined), true);
  assert.equal(unrestricted(['VISA']), false);
  assert.deepEqual(toggleConditionValue([], 'VISA'), ['VISA'], '从不限选中一个具体值');
  assert.deepEqual(toggleConditionValue(['VISA', 'MASTERCARD'], 'VISA'), ['MASTERCARD']);
  assert.deepEqual(toggleConditionValue(['VISA'], 'VISA'), [], '取消最后一个回到不限');
  assert.deepEqual(toggleConditionValue(['VISA'], ' '), ['VISA']);
  assert.deepEqual(addConditionValue(['VISA'], ' mada '), ['VISA', 'MADA']);
  assert.deepEqual(addConditionValue(['VISA'], 'visa'), ['VISA']);
  const options = conditionOptions('CARD_BRAND', ['MASTERCARD', 'ELO']);
  assert.deepEqual([options[0], options.at(-1), options.includes('MASTERCARD')], ['VISA', 'ELO', true]);
  assert.deepEqual(conditionOptions('PAYOUT_RAIL', ['SWIFT']), ['SWIFT']);
  assert.deepEqual([conditionValueLabel('FUNDING_TYPE', 'DEBIT'), conditionValueLabel('CONSUMER_COUNTRY', 'AE'), conditionValueLabel('CARD_BRAND', 'VISA'),
    conditionValueLabel('ISSUER_COUNTRY', 'ZZ')], ['Debit', 'United Arab Emirates', 'VISA', 'ZZ']);
});

test('F4 必填维度在前：条件区与汇总按「必填在前、其余目录顺序」，提交 body 仍按目录顺序', () => {
  const dims: CapabilityDimension[] = [{ dimensionCode: 'CARD_BRAND', required: false }, { dimensionCode: 'FUNDING_TYPE', required: false },
    { dimensionCode: 'CURRENCY', required: true }, { dimensionCode: 'CONSUMER_COUNTRY', required: false }];
  assert.deepEqual(orderedDimensions(dims).map(d => d.dimensionCode), ['CURRENCY', 'CARD_BRAND', 'FUNDING_TYPE', 'CONSUMER_COUNTRY']);
  assert.deepEqual(dims.map(d => d.dimensionCode), ['CARD_BRAND', 'FUNDING_TYPE', 'CURRENCY', 'CONSUMER_COUNTRY'], '不改原数组');
  assert.equal(conditionSummary(dims, { CURRENCY: ['AED'], CARD_BRAND: ['VISA'] }, false),
    'Accepts: Currency AED · Card Brand VISA · Any Funding Type · Any Consumer Country');
  assert.deepEqual(conditionsBody(dims, { CURRENCY: ['AED'], CARD_BRAND: ['VISA'] }).map(c => c.dimensionCode), ['CARD_BRAND', 'CURRENCY'], '提交顺序不受影响');
  assert.deepEqual(orderedDimensions([]), []);
});

test('F5 / O2 币种下拉：只含快捷标签以外的常用币种「代码 中文名」，清单外已选币种追加且不丢；汇总显示代码', () => {
  assert.equal(currencyCodes.length, 20);
  const options = currencySelectOptions(['usd', 'XAF', 'KWD']);
  assert.deepEqual(options.find(option => option.value === 'KWD'), { value: 'KWD', label: 'KWD Kuwaiti Dinar' });
  assert.deepEqual(options.at(-1), { value: 'XAF', label: 'XAF' }, '清单外的已选币种仍显示');
  assert.equal(options.filter(option => option.value === 'KWD').length, 1, '清单内的已选币种不重复');
  assert.deepEqual(['AED', 'USD', 'SAR', 'EUR', 'GBP'].filter(code => options.some(option => option.value === code)), [], '快捷币种不在下拉中');
  assert.equal(currencySelectOptions(undefined).length, 15);
  assert.equal(conditionSummary([{ dimensionCode: 'CURRENCY', required: true }], { CURRENCY: ['KWD'] }, false), 'Accepts: Currency KWD');
});

test('O2 币种拆分 / 合并：快捷与下拉各管各的，合并先快捷后下拉，不丢不重', () => {
  assert.deepEqual(splitCurrencies(['kwd', 'AED', 'XAF', 'USD', 'AED']), { quick: ['AED', 'USD'], more: ['KWD', 'XAF'] });
  assert.deepEqual(splitCurrencies(undefined), { quick: [], more: [] });
  assert.deepEqual(mergeCurrencies(['AED', 'USD'], ['KWD', 'XAF']), ['AED', 'USD', 'KWD', 'XAF']);
  assert.deepEqual(mergeCurrencies(['AED'], ['AED', 'kwd']), ['AED', 'KWD'], '重复去掉');
  const values = ['KWD', 'AED', 'XAF'];
  const { quick, more } = splitCurrencies(values);
  assert.deepEqual(mergeCurrencies(quick, more).sort(), [...values].sort(), '拆分再合并不丢值');
  assert.deepEqual(mergeCurrencies(toggleConditionValue(quick, 'USD'), more), ['AED', 'USD', 'KWD', 'XAF'], '点快捷标签不影响下拉部分');
  assert.deepEqual(mergeCurrencies(quick, ['XAF']), ['AED', 'XAF'], '下拉取消 KWD 不影响快捷部分');
});

test('F2 条件汇总：按可配维度顺序，空维度为「不限」，必填为空为「未选」，开启 3DS 追加', () => {
  const dims: CapabilityDimension[] = [{ dimensionCode: 'CURRENCY', required: true }, { dimensionCode: 'FUNDING_TYPE', required: false },
    { dimensionCode: 'CARD_BRAND', required: false }, { dimensionCode: 'CONSUMER_COUNTRY', required: false }];
  assert.equal(conditionSummary(dims, { CURRENCY: ['AED', 'usd'], FUNDING_TYPE: ['DEBIT'], CARD_BRAND: [] }, true),
    'Accepts: Currency AED, USD · Funding Type Debit · Any Card Brand · Any Consumer Country · 3DS Required');
  assert.equal(conditionSummary(dims, {}, false, definitions), 'Accepts: Currency not selected · Any Funding Type · Any Card Brand · Any Consumer Country');
  assert.equal(conditionSummary(dims, { CURRENCY: ['AED'], CONSUMER_COUNTRY: ['AE', 'SA'] }, undefined),
    'Accepts: Currency AED · Any Funding Type · Any Card Brand · Consumer Country United Arab Emirates, Saudi Arabia');
  assert.equal(conditionSummary([], undefined, false), 'Accepts: Any');
  assert.equal(conditionSummary([], undefined, true), 'Accepts: 3DS Required');
});

test('解码：开通项读回 conditions 与 require3ds；维度清单缺省名称时用代码', () => {
  const agreement = decodeAgreement({ agreementId: '7319000000000000101', agreementNo: 'AGR-1', agreementName: '示例', agreementType: 'CLIENT',
    status: 'ACTIVE', effectiveFrom: '2026-10-01T00:00:00Z', changeVersion: 3, clientId: '11', entityNo: 'AE001', parties: [], documents: [],
    productCodes: ['ACQUIRING'], capabilities: [{ ...capability(), require3ds: undefined }] });
  assert.equal(agreement.capabilities[0].require3ds, false);
  assert.throws(() => decodeAgreement({ ...agreement, capabilities: [{ ...capability(), conditions: [{ dimensionCode: 'CURRENCY', values: [] }] }] }), ApiError,
    '条件至少一个取值');
  assert.throws(() => decodeAgreement({ ...agreement, capabilities: [{ ...capability(), conditions: undefined, limits: [] }] }), ApiError, '旧 limits 不再接受');
  const dims = decodeDimensions({ dimensions: [{ dimensionCode: 'CURRENCY', factSource: 'ORDER', evaluationPoint: 'CHECKOUT', acceptanceAllowed: true,
    pricingAllowed: true }, ...definitions] });
  assert.deepEqual(dims.map(d => d.displayName), ['CURRENCY', '卡品牌', '新维度']);
  assert.throws(() => decodeDimensions({ dimensions: [{ dimensionCode: 'CURRENCY' }] }), ApiError);
});

test('卡维度建议值：卡组织 8 个规范值、借贷记含 CHARGE、发卡区域为 6 个地理区域（不含境内 / 境外）', () => {
  assert.deepEqual(dimensionSuggestions.CARD_BRAND, ['VISA', 'MASTERCARD', 'AMEX', 'UNIONPAY', 'MADA', 'DISCOVER', 'JCB', 'DINERS']);
  assert.deepEqual(dimensionSuggestions.FUNDING_TYPE, ['CREDIT', 'DEBIT', 'PREPAID', 'CHARGE']);
  assert.deepEqual(dimensionSuggestions.ISSUER_REGION, ['GCC', 'MEA', 'EUROPE', 'NORTH_AMERICA', 'LATAM', 'APAC']);
  assert.equal(conditionValueLabel('ISSUER_REGION', 'NORTH_AMERICA'), 'North America');
  assert.equal(conditionValueLabel('ISSUER_REGION', 'DOMESTIC'), 'DOMESTIC');
  assert.equal(conditionValueLabel('FUNDING_TYPE', 'CHARGE'), 'Charge');
});
