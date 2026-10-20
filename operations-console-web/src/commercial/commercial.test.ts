import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import {
  acceptanceBody, agreementActions, agreementBody, bandIssues, capabilityBody, commercialButtons, commercialErrorMessage, commercialPaths, commercialRoutes, componentSummary,
  coverageGaps, decodeAgreementOverview, decodeAgreementWrite, decodeCapabilityWrite, decodePlan, decodeProfileList, decodeVersion, decodeWorkspace,
  fieldPath, hasButton, newPriceDraft, percent, planBody, priceDraft, priceMatrix, pricesBody, profileBody, reserveError, utcInstant, versionActions,
} from './commercial.ts';
import type { AgreementCapability, Price } from './commercial.ts';

const agreementId = '7319000000000000101';
const versionId = '7319000000000000601';
const capability = (entityNo: string, currencies: string[], status = 'ENABLED') => ({ agreementCapabilityId: '7319000000000000301', entityNo,
  capabilityCode: 'CARD_VISA', status, effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, rollingReserveRate: null, rollingReserveDays: null,
  limits: currencies.map(limitValue => ({ limitCode: 'CURRENCY', limitValue })) });
const agreement = {
  agreementId, agreementNo: 'AGR-1', agreementName: '示例合同', agreementType: 'CLIENT', businessMode: 'MOR', fundMode: 'SETTLE_THROUGH_PLATFORM',
  pricingAuthority: 'PLATFORM', referralPartnerId: null, clientId: '7300000000000000011', localPartnerId: null, entityNo: 'AE001', operatingEntityNos: [],
  status: 'ACTIVE', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, signedTime: '2026-10-02T00:00:00Z', activatedTime: '2026-10-03T00:00:00Z',
  changeVersion: 7,
  parties: [{ agreementPartyId: '7319000000000000221', partyId: '7319000000000000201', partyProfileId: '7319000000000000202', partyRole: 'CLIENT',
    acceptanceMethod: 'E_SIGNATURE', acceptedByActorId: null, acceptedAt: '2026-10-02T00:00:00Z', acceptanceReference: 'ESIGN-1', evidenceDocumentId: '7319000000000000231' }],
  documents: [{ agreementDocumentId: '7319000000000000231', documentType: 'SIGNING_DOCUMENT', fileId: '7319000000000000701', contentHash: 'a'.repeat(64) }],
  productCodes: ['ACQUIRING'], capabilities: [capability('AE001', ['USD', 'AED'])],
};
const termVersion = { planId: '7319000000000000501', termType: 'CLIENT_PRICING', versionId, versionNo: '1', status: 'ACTIVE', timing: 'CURRENT',
  effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE' };
const version = { versionId, planId: '7319000000000000501', termType: 'CLIENT_PRICING', agreementId, versionNo: '2', status: 'DRAFT',
  effectiveFrom: '2026-10-19T08:00:00Z', effectiveTo: null, roundingMode: 'HALF_UP', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', changeVersion: '5' };
const component = (code: string, trigger: string, extra: Record<string, unknown> = {}) => ({ componentId: '1', componentNo: 1, componentCode: code,
  componentType: 'PERCENTAGE', triggerCode: trigger, calculationBase: 'GROSS_AMOUNT', refundBehavior: 'RETURN_PRORATA', rate: '0.029', fixedAmount: null,
  currency: 'USD', minAmount: '0.50', maxAmount: null, bandBasis: null, bandMode: null, bands: [], ...extra });
const price = (entityNo: string, currency: string, priorityNo: number, components = [component('MDR', 'CAPTURE')]) => ({
  termType: 'CLIENT_PRICING', agreementId, planId: '7319000000000000501', termVersionId: versionId, entityNo, clientId: '7300000000000000011',
  localPartnerId: null, priceId: `73190000000000008${priorityNo}`, pricingProfileCode: 'CARD_VISA_CREDIT', paymentMethodCode: 'CARD', priorityNo, currency,
  pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', conditions: [{ dimensionCode: 'CARD_BRAND', conditionValue: 'VISA' }], components,
  settlement: { settlementBusinessDays: 2, businessCalendarCode: 'AE_BUSINESS_DAYS', settlementTimezone: 'UTC', cutoffSecondOfDay: 0 } });
const workspace = (prices: unknown[]) => ({ version, prices, agreement: { agreementId, agreementNo: 'AGR-1', agreementName: '示例合同', agreementType: 'CLIENT',
  status: 'ACTIVE', clientId: '7300000000000000011', localPartnerId: null, entityNos: ['AE001'], capabilities: [capability('AE001', ['USD', 'AED'])] } });
const resource = (resourceCode: string, resourceType: 'MENU' | 'BUTTON' = 'BUTTON') => ({ resourceId: resourceCode, resourceCode, resourceType,
  parentResourceId: null, displayName: resourceCode, frontendRoute: null, componentKey: null, sortOrder: 0 });

test('页面注册：合同、定价画像、价目及工作台、费率预览、价格试算、产品目录、变更历史；详情路由登记在菜单路径', () => {
  const pages = pageCapabilities.filter(page => page.componentKey.startsWith('OPERATIONS_COMMERCIAL_'));
  assert.deepEqual(pages.map(page => [page.componentKey, page.frontendRoute]), [
    ['OPERATIONS_COMMERCIAL_AGREEMENTS', commercialRoutes.agreements], ['OPERATIONS_COMMERCIAL_PRICING_PROFILES', commercialRoutes.profiles],
    ['OPERATIONS_COMMERCIAL_TERMS', commercialRoutes.terms], ['OPERATIONS_COMMERCIAL_WORKBENCH', commercialRoutes.workbench],
    ['OPERATIONS_COMMERCIAL_RATE_CARDS', commercialRoutes.rateCards], ['OPERATIONS_COMMERCIAL_PRICE_SIMULATOR', commercialRoutes.priceSimulator],
    ['OPERATIONS_COMMERCIAL_CATALOG', commercialRoutes.catalog], ['OPERATIONS_COMMERCIAL_HISTORY', commercialRoutes.history]]);
  assert.deepEqual(pages.map(page => page.actions.map(action => action.resourceCode)), [
    [commercialButtons.agreementManage], [commercialButtons.profileManage], [commercialButtons.termManage, commercialButtons.termApprove], [], [], [], [], []]);
  const menu = (componentKey: string, frontendRoute: string) => ({ ...resource(componentKey, 'MENU'), componentKey, frontendRoute });
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_COMMERCIAL_AGREEMENTS', '/commercial/agreements')), ['/commercial/agreements', '/commercial/agreements/:agreementId']);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_COMMERCIAL_TERMS', '/commercial/terms')), ['/commercial/terms', '/commercial/terms/versions/:versionId',
    '/commercial/terms/compare/:baseVersionId/:targetVersionId']);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_COMMERCIAL_PRICING_PROFILES', '/commercial/pricing-profiles')), ['/commercial/pricing-profiles']);
});

test('BFF 路径：/commercial-admin/v1 下的合同、画像与条款端点', () => {
  assert.equal(commercialPaths.acceptance(agreementId, '1'), `/commercial-admin/v1/agreements/${agreementId}/parties/1/acceptance`);
  assert.equal(commercialPaths.versionAction(versionId, 'approve'), `/commercial-admin/v1/commercial-terms/versions/${versionId}/approve`);
  assert.equal(commercialPaths.profileAction('9', 'retire'), '/commercial-admin/v1/pricing-profiles/9/retire');
  assert.equal(commercialPaths.versions('5'), '/commercial-admin/v1/commercial-terms/plans/5/versions');
});

test('按钮权限：只认 BUTTON 资源；Maker 与 Checker 动作分属维护与审批', () => {
  assert.equal(hasButton([resource(commercialButtons.termManage)], commercialButtons.termManage), true);
  assert.equal(hasButton([resource(commercialButtons.termManage, 'MENU')], commercialButtons.termManage), false);
  const maker = { canManage: true, canApprove: false }; const checker = { canManage: false, canApprove: true };
  assert.deepEqual(versionActions('DRAFT', maker), { editPrices: true, submit: true, approve: false, reject: false, schedule: false, activate: false, copy: true });
  assert.deepEqual(versionActions('PENDING_APPROVAL', maker), { editPrices: false, submit: false, approve: false, reject: false, schedule: false, activate: false, copy: true });
  assert.deepEqual(versionActions('PENDING_APPROVAL', checker), { editPrices: false, submit: false, approve: true, reject: true, schedule: false, activate: false, copy: false });
  assert.equal(versionActions('APPROVED', maker).schedule, true);
  assert.equal(versionActions('APPROVED', checker).schedule, false);
  assert.equal(versionActions('SCHEDULED', maker).activate, true);
  assert.deepEqual(agreementActions('DRAFT', true), { submit: true, accept: false, activate: false, terminate: false, products: true, capabilities: true });
  assert.deepEqual(agreementActions('PENDING_SIGNATURE', true), { submit: false, accept: true, activate: false, terminate: false, products: true, capabilities: true });
  assert.equal(agreementActions('SIGNED', true).activate, true);
  assert.equal(agreementActions('ACTIVE', true).terminate, true);
  assert.deepEqual(agreementActions('TERMINATED', true), { submit: false, accept: false, activate: false, terminate: false, products: false, capabilities: false });
  assert.deepEqual(agreementActions('ACTIVE', false), { submit: false, accept: false, activate: false, terminate: false, products: false, capabilities: false });
});

test('合同详情聚合解码：合同 ID 一致、可空字段归一为 null、版本部分状态与列表一致', () => {
  const overview = decodeAgreementOverview(agreementId)({ agreement, termVersionsStatus: 'AVAILABLE', termVersions: [termVersion] });
  assert.equal(overview.agreement.capabilities[0].limits.length, 2);
  assert.equal(overview.termVersions[0].timing, 'CURRENT');
  const partner = decodeAgreementOverview(agreementId)({ agreement: { ...agreement, agreementType: 'PARTNER', entityNo: undefined, operatingEntityNos: undefined },
    termVersionsStatus: 'NOT_APPLICABLE', termVersions: [] });
  assert.equal(partner.agreement.entityNo, null);
  assert.deepEqual(partner.agreement.operatingEntityNos, []);
  assert.throws(() => decodeAgreementOverview('7319000000000000999')({ agreement, termVersionsStatus: 'AVAILABLE', termVersions: [] }), ApiError);
  assert.throws(() => decodeAgreementOverview(agreementId)({ agreement, termVersionsStatus: 'UNAVAILABLE', termVersions: [termVersion] }), ApiError, '不可用时不得带版本');
  assert.throws(() => decodeAgreementOverview(agreementId)({ agreement: { ...agreement, status: 'PAUSED' }, termVersionsStatus: 'NOT_APPLICABLE', termVersions: [] }), ApiError);
  assert.throws(() => decodeAgreementOverview(agreementId)({ agreement: { ...agreement, changeVersion: '7' }, termVersionsStatus: 'NOT_APPLICABLE', termVersions: [] }), ApiError);
});

test('写入回执解码：合同、开通项、方案、版本与画像的身份与状态严格校验', () => {
  assert.equal(decodeAgreementWrite(agreementId)({ agreementId, agreementNo: 'AGR-1', status: 'ACTIVE', changeVersion: 8 }).changeVersion, 8);
  assert.throws(() => decodeAgreementWrite(agreementId)({ agreementId: '1', agreementNo: 'AGR-1', status: 'ACTIVE', changeVersion: 8 }), ApiError);
  assert.equal(decodeCapabilityWrite(agreementId)({ agreementId, agreementCapabilityId: '3', status: 'SUSPENDED', changeVersion: 9 }).status, 'SUSPENDED');
  assert.throws(() => decodeCapabilityWrite(agreementId)({ agreementId, agreementCapabilityId: '3', status: 'PAUSED', changeVersion: 9 }), ApiError);
  assert.equal(decodePlan({ planId: '5', planCode: 'CP-1', termType: 'PARTNER_SHARE', agreementId: null, entityNo: null, clientId: null,
    localPartnerId: '21', status: 'DRAFT', changeVersion: '1' }).agreementId, null);
  assert.equal(decodeVersion(versionId)(version).status, 'DRAFT');
  assert.throws(() => decodeVersion('1')(version), ApiError);
  assert.throws(() => decodeVersion()({ ...version, changeVersion: 5 }), ApiError, '条款版本令牌是十进制字符串');
  const profile = { pricingProfileId: '4', pricingProfileCode: 'CARD_VISA_CREDIT', profileName: 'Visa', profileKind: 'PAYMENT_METHOD', paymentMethodCode: 'CARD',
    status: 'ACTIVE', changeVersion: 3, conditions: [{ dimensionCode: 'CARD_BRAND', conditionValue: 'VISA' }] };
  assert.equal(decodeProfileList({ profiles: [profile] })[0].conditions.length, 1);
  assert.throws(() => decodeProfileList({ profiles: [{ ...profile, conditions: [{ dimensionCode: 'MCC', conditionValue: '5411' }] }] }), ApiError);
  assert.throws(() => decodeProfileList([profile]), ApiError);
});

test('版本编辑页解码：版本与合同一致，价格只属于合同范围内 OE', () => {
  const decoded = decodeWorkspace(versionId)(workspace([price('AE001', 'USD', 1)]));
  assert.equal(decoded.prices[0].settlement?.settlementBusinessDays, 2);
  assert.equal(decoded.agreement.localPartnerId, null);
  assert.throws(() => decodeWorkspace(versionId)(workspace([price('SA001', 'USD', 1)])), ApiError, '合同范围外的 OE');
  assert.throws(() => decodeWorkspace(versionId)({ ...workspace([]), version: { ...version, agreementId: '1' } }), ApiError);
  assert.throws(() => decodeWorkspace(versionId)(workspace([price('AE001', 'USD', 1, [])])), ApiError, '价格至少一个组件');
});

test('价格矩阵：按 OE、币种、优先级排行，按计费事件分列，同事件多组件同格', () => {
  const prices = decodeWorkspace(versionId)(workspace([
    price('AE001', 'USD', 2, [component('MDR', 'CAPTURE'), component('FIXED_FEE', 'CAPTURE', { componentType: 'FIXED', rate: null, fixedAmount: '0.30', minAmount: null })]),
    price('AE001', 'AED', 1, [component('REFUND_FEE', 'REFUND', { componentType: 'FIXED', rate: null, fixedAmount: '1.00', currency: 'AED', minAmount: null })]),
    price('AE001', 'USD', 1),
  ])).prices;
  const matrix = priceMatrix(prices);
  assert.deepEqual(matrix.events, ['CAPTURE', 'REFUND']);
  assert.deepEqual(matrix.rows.map(row => `${row.currency}/${row.priorityNo}`), ['AED/1', 'USD/1', 'USD/2']);
  assert.equal(matrix.rows[2].events.CAPTURE.length, 2);
  assert.equal(matrix.rows[0].events.CAPTURE, undefined);
});

test('组件摘要与费率显示：不做浮点舍入', () => {
  assert.equal(percent('0.029'), '2.9%');
  assert.equal(percent('0.0275'), '2.75%');
  assert.equal(percent('1'), '100%');
  assert.equal(percent('0.00005'), '0.005%');
  assert.equal(percent(null), '—');
  assert.equal(componentSummary({ componentType: 'PERCENTAGE', rate: '0.029', fixedAmount: null, currency: 'USD', minAmount: '0.50', maxAmount: null, bands: [], bandMode: null }),
    '2.9% (min 0.50 USD)');
  assert.equal(componentSummary({ componentType: 'FIXED', rate: null, fixedAmount: '0.30', currency: 'USD', minAmount: null, maxAmount: null, bands: [], bandMode: null }), '0.30 USD');
  assert.equal(componentSummary({ componentType: 'TIERED', rate: null, fixedAmount: null, currency: 'USD', minAmount: null, maxAmount: null, bandMode: 'WHOLE_BASE',
    bands: [{ bandId: '1', bandNo: 1, lowerBound: '0', upperBound: '1000', rate: '0.01', fixedAmount: null, currency: 'USD' }] }), '阶梯 1 档 · WHOLE_BASE');
});

test('覆盖提示：ENABLED 开通项的每个约束币种都应有同 OE 价格，暂停项与非币种约束不计', () => {
  const capabilities = [capability('AE001', ['USD', 'AED']), capability('AE001', ['EUR'], 'SUSPENDED')] as AgreementCapability[];
  assert.deepEqual(coverageGaps(capabilities, [{ entityNo: 'AE001', currency: 'USD' }]), ['AE001/AED（CARD_VISA）']);
  assert.deepEqual(coverageGaps(capabilities, [{ entityNo: 'AE001', currency: 'USD' }, { entityNo: 'AE001', currency: 'AED' }]), []);
});

test('价格草稿往返：读模型去掉主键与序号，PUT 请求按组件类型清理无关字段', () => {
  const tiered = component('VOLUME', 'CAPTURE', { componentType: 'TIERED', rate: '0.5', minAmount: '1', bandBasis: 'TRANSACTION_AMOUNT', bandMode: 'WHOLE_BASE',
    bands: [{ bandId: '1', bandNo: 1, lowerBound: '0', upperBound: '1000', rate: '0.01', fixedAmount: null, currency: 'USD' },
      { bandId: '2', bandNo: 2, lowerBound: '1000', upperBound: null, rate: '0.005', fixedAmount: null, currency: 'USD' }] });
  const decoded = decodeWorkspace(versionId)(workspace([price('AE001', 'USD', 1, [component('MDR', 'CAPTURE', { fixedAmount: '9' }), tiered])])).prices[0] as Price;
  const draft = priceDraft(decoded);
  assert.equal(draft.components[0].bands, null);
  assert.deepEqual(draft.components[1].bands, [{ lowerBound: '0', upperBound: '1000', rate: '0.01', fixedAmount: null },
    { lowerBound: '1000', upperBound: null, rate: '0.005', fixedAmount: null }]);
  const body = pricesBody('5', [{ ...draft, components: [{ ...draft.components[0], componentCode: ' mdr '.trim().toUpperCase(), maxAmount: ' ' }, draft.components[1]] }]);
  assert.equal(body.expectedChangeVersion, '5');
  const [percentage, bands] = body.prices[0].components;
  assert.deepEqual([percentage.rate, percentage.fixedAmount, percentage.maxAmount, percentage.bands], ['0.029', null, null, null], '百分比不带固定额与档位');
  assert.deepEqual([bands.rate, bands.minAmount, bands.bandBasis, bands.bands?.length], [null, null, 'TRANSACTION_AMOUNT', 2], '阶梯头部不带费率与上下限');
  assert.equal(Object.hasOwn(body.prices[0], 'priceId'), false);
  assert.equal(Object.hasOwn(body, 'operatorAccountId'), false, '操作人由会话决定');
});

test('新价格默认草稿：Client 条款带结算四字段，Partner 条款为空', () => {
  const client = newPriceDraft('CLIENT_PRICING', 'AE001', 'USD', 'CARD_VISA_CREDIT', 1);
  assert.deepEqual([client.settlementBusinessDays, client.businessCalendarCode, client.settlementTimezone, client.cutoffSecondOfDay], [2, 'AE_BUSINESS_DAYS', 'UTC', 0]);
  const partner = newPriceDraft('PARTNER_SHARE', 'AE001', 'USD', 'CARD_VISA_CREDIT', 1);
  assert.deepEqual([partner.settlementBusinessDays, partner.businessCalendarCode, partner.settlementTimezone, partner.cutoffSecondOfDay], [null, null, null, null]);
});

test('档位检查：首档 0、连续、末档无上界、费率与金额恰好一个、累进只允许费率', () => {
  const band = (lowerBound: string, upperBound: string | null, rate: string | null, fixedAmount: string | null = null) => ({ lowerBound, upperBound, rate, fixedAmount });
  assert.deepEqual(bandIssues([band('0', '1000', '0.01'), band('1000', null, '0.005')], 'WHOLE_BASE'), []);
  assert.deepEqual(bandIssues([], 'WHOLE_BASE'), ['阶梯组件至少一档']);
  assert.deepEqual(bandIssues([band('10', null, '0.01')], 'WHOLE_BASE'), ['首档下界必须为 0']);
  assert.deepEqual(bandIssues([band('0', '1000', '0.01'), band('1500', null, '0.005')], 'WHOLE_BASE'), ['第 1 档上界必须等于第 2 档下界']);
  assert.deepEqual(bandIssues([band('0', '1000', '0.01'), band('1000', '2000', '0.005')], 'WHOLE_BASE'), ['末档上界必须为空']);
  assert.deepEqual(bandIssues([band('0', null, '0.01', '1')], 'WHOLE_BASE'), ['第 1 档费率与固定金额必须恰好填一个']);
  assert.deepEqual(bandIssues([band('0', null, null, '1')], 'PROGRESSIVE'), ['累进模式第 1 档只允许费率']);
  assert.deepEqual(bandIssues([band('0', '1000.00', '0.01'), band('1000', null, '0.005')], 'WHOLE_BASE'), [], '数值相等即连续');
});

test('合同请求：CLIENT 固定资金模式与定价权，PARTNER 清空 Client 字段；签署文件可后补', () => {
  const client = agreementBody({ agreementNo: ' AGR-1 ', agreementName: '示例', agreementType: 'CLIENT', businessMode: 'PSP', clientId: '11', entityNo: 'AE001',
    effectiveFrom: '2026-11-01T00:00:00Z', parties: [{ partyId: '1', partyProfileId: '2', partyRole: 'CLIENT' }], signingFileId: '7', signingContentHash: 'A'.repeat(64) });
  assert.deepEqual([client.agreementNo, client.fundMode, client.pricingAuthority, client.localPartnerId, client.operatingEntityNos],
    ['AGR-1', 'SETTLE_THROUGH_PLATFORM', 'PLATFORM', null, null]);
  assert.equal(Date.parse(client.effectiveFrom), Date.parse('2026-11-01T00:00:00Z'));
  assert.match(client.effectiveFrom, /[+-]\d{2}:\d{2}$/, '带时区偏移');
  assert.deepEqual(client.documents, [{ documentType: 'SIGNING_DOCUMENT', fileId: '7', contentHash: 'a'.repeat(64) }]);
  const partner = agreementBody({ agreementNo: 'AGR-2', agreementName: '伙伴', agreementType: 'PARTNER', businessMode: 'MOR', clientId: '11', localPartnerId: '21',
    operatingEntityNos: ['AE001', ' SA001 '], effectiveFrom: '2026-11-01T00:00:00Z', parties: [{ partyId: '1', partyProfileId: '2', partyRole: 'LOCAL_PARTNER' }] });
  assert.deepEqual([partner.businessMode, partner.fundMode, partner.clientId, partner.entityNo, partner.documents], [null, null, null, null, null]);
  assert.deepEqual(partner.operatingEntityNos, ['AE001', 'SA001']);
});

test('签署、开通项、画像与方案请求的条件字段', () => {
  const click = acceptanceBody(3, { acceptanceReference: 'R-1', acceptanceMethod: 'CLICK_ACCEPTANCE', acceptedByActorId: '9', acceptedAt: '2026-10-19T10:00:00Z', fileId: '1', contentHash: 'x' });
  assert.deepEqual([click.acceptedByActorId, click.executedDocument], ['9', null]);
  const esign = acceptanceBody(3, { acceptanceReference: 'R-2', acceptanceMethod: 'E_SIGNATURE', acceptedByActorId: '9', acceptedAt: '2026-10-19T10:00:00Z', fileId: '1', contentHash: 'AB' });
  assert.deepEqual([esign.acceptedByActorId, esign.executedDocument], [null, { fileId: '1', contentHash: 'ab' }]);
  const created = capabilityBody(6, { entityNo: 'AE001', capabilityCode: 'CARD_VISA', effectiveFrom: '2026-11-01T00:00:00Z', rollingReserveRate: ' ', rollingReserveDays: null,
    limits: [{ limitCode: 'CURRENCY', limitValue: 'usd' }, { limitCode: 'CURRENCY', limitValue: 'USD' }, { limitCode: 'CONSUMER_COUNTRY', limitValue: ' ' }] }, false);
  assert.deepEqual(created.limits, [{ limitCode: 'CURRENCY', limitValue: 'USD' }], '去空白、转大写并去重');
  assert.equal(created.rollingReserveRate, null);
  assert.equal(Object.hasOwn(created, 'status'), false);
  const updated = capabilityBody(7, { entityNo: 'AE001', capabilityCode: 'CARD_VISA', status: 'SUSPENDED', effectiveFrom: '2026-11-01T00:00:00Z', limits: [] }, true);
  assert.deepEqual([(updated as { status?: string }).status, Object.hasOwn(updated, 'entityNo')], ['SUSPENDED', false]);
  assert.equal(reserveError('0.1', null), '保证金比例与天数必须同时填写或同时留空');
  assert.equal(reserveError('0.1', 90), null);
  assert.equal(reserveError(undefined, undefined), null);
  assert.deepEqual(profileBody({ pricingProfileCode: ' card_visa ', profileKind: 'FX_PAIR', profileName: ' FX ', paymentMethodCode: 'CARD',
    conditions: [{ dimensionCode: 'CURRENCY_PAIR', conditionValue: 'usd/aed' }] }), { pricingProfileCode: 'CARD_VISA', profileKind: 'FX_PAIR', profileName: 'FX',
    paymentMethodCode: null, conditions: [{ dimensionCode: 'CURRENCY_PAIR', conditionValue: 'USD/AED' }] });
  assert.deepEqual(profileBody({ profileKind: 'PAYMENT_METHOD', profileName: 'Visa', paymentMethodCode: 'card', conditions: [] }, 2),
    { expectedChangeVersion: 2, profileName: 'Visa', paymentMethodCode: 'CARD', conditions: [] });
  assert.deepEqual(planBody({ planCode: 'P-1', termType: 'PARTNER_SHARE', agreementId: '5', ownerId: '21' }),
    { planCode: 'P-1', termType: 'PARTNER_SHARE', agreementId: '5', clientId: null, localPartnerId: '21' });
  assert.deepEqual(planBody({ planCode: 'P-2', termType: 'CLIENT_PRICING', agreementId: '5', ownerId: '11' }),
    { planCode: 'P-2', termType: 'CLIENT_PRICING', agreementId: '5', clientId: '11', localPartnerId: null });
});

test('排期时刻：本地时间转换为 UTC RFC 3339（Z 结尾、无毫秒）', () => {
  const utc = utcInstant('2026-11-01T08:30:00');
  assert.match(utc, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
  assert.equal(Date.parse(utc), new Date('2026-11-01T08:30:00').getTime());
});

test('错误提示：409 提示重新加载，BUSINESS_CHECK_FAILED 按场景给出可操作说明，其他错误交给通用通知', () => {
  assert.match(commercialErrorMessage(new ApiError(409, 'CONCURRENT_REQUEST'), 'version') ?? '', /重新加载/);
  assert.match(commercialErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED'), 'version') ?? '', /Checker 不能与 Maker 相同/);
  assert.match(commercialErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', '业务校验未通过'), 'version') ?? '', /Checker 不能与 Maker 相同/);
  assert.equal(commercialErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', '激活校验：开通项 AE001/APPLE_PAY/AED 无价格'), 'version'),
    '业务校验未通过：激活校验：开通项 AE001/APPLE_PAY/AED 无价格');
  assert.match(commercialErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED'), 'agreement') ?? '', /签署文件/);
  assert.equal(commercialErrorMessage(new ApiError(503, 'DEPENDENCY_UNAVAILABLE'), 'profile'), null);
  assert.equal(commercialErrorMessage(new Error('x'), 'profile'), null);
});

test('字段路径：commercial 422 的数组路径映射为表单名路径', () => {
  assert.deepEqual(fieldPath('prices[0].components[1].bands[2].rate'), ['prices', 0, 'components', 1, 'bands', 2, 'rate']);
  assert.deepEqual(fieldPath('agreementNo'), ['agreementNo']);
});
