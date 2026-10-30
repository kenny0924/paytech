import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { commercialPaths, decodePrerequisiteCheckResult } from './commercial.ts';
import type { CapabilityExecutionStatus, CapabilityStatus, PrerequisiteCheckResult } from './commercial.ts';
import {
  blockedCapabilities, blockedSummary, capabilityGateActions, effectiveExecutionStatus, freshPrerequisites, lastSuspension, lineCounts, needsInitialCheck,
  prerequisiteLines, prerequisiteReason, prerequisiteShortReason, sectionCounts,
} from './capabilityGate.ts';

const agreementId = '7319000000000000101';
const client = { agreementType: 'CLIENT' as const };
const row = (status: CapabilityStatus, executionStatus: CapabilityExecutionStatus | null) => ({ status, executionStatus });
const context = { clientId: '7300000000000000011', entityNo: 'AE001', agreementStatus: 'ACTIVE' };
const response = {
  agreementId, changeVersion: 11, capabilities: [
    { agreementCapabilityId: '7319000000000000301', entityNo: 'AE001', capabilityCode: 'CARD', capabilityKind: 'PAYMENT_METHOD', status: 'ACTIVE',
      executionStatus: 'PENDING_PREREQUISITES', checks: [
        { code: 'QUALIFICATION', result: 'UNSATISFIED', reasonCode: 'OPERATING_ENTITY_NOT_QUALIFIED', params: { subjectType: 'OPERATING_ENTITY', subjectId: 'SA001' } },
        { code: 'AGREEMENT_TERM', result: 'SATISFIED', reasonCode: null, params: {} },
        { code: 'CHANNEL_IDENTITY', result: 'SKIPPED', reasonCode: 'AWAITING_PRICE', params: { entityNo: 'AE001' } }] },
    { agreementCapabilityId: '7319000000000000302', entityNo: 'AE001', capabilityCode: 'REFUND', capabilityKind: 'FEATURE', status: 'DISABLED',
      executionStatus: null, checks: [] }],
};

test('AC-10 行内操作：有查看权限即可展开还差什么；暂停除手动暂停外均可、恢复只在手动暂停且需维护权限；关闭项与 PARTNER 合同没有门禁操作', () => {
  const pick = (status: CapabilityStatus, execution: CapabilityExecutionStatus | null) => {
    const actions = capabilityGateActions(client, row(status, execution), true, true);
    return [actions.expandable, actions.suspend, actions.resume];
  };
  assert.deepEqual(pick('ACTIVE', 'PENDING_PREREQUISITES'), [true, true, false]);
  assert.deepEqual(pick('ACTIVE', 'SUSPENDED_BY_PREREQUISITE'), [true, true, false]);
  assert.deepEqual(pick('ACTIVE', 'ENABLED'), [true, true, false]);
  assert.deepEqual(pick('ACTIVE', 'SUSPENDED_MANUALLY'), [true, false, true]);
  assert.deepEqual(pick('DISABLED', null), [false, false, false]);
  assert.deepEqual(Object.values(capabilityGateActions({ agreementType: 'PARTNER' }, row('ACTIVE', 'ENABLED'), true, true)), [false, false, false]);
  assert.deepEqual(Object.values(capabilityGateActions(client, row('ACTIVE', 'PENDING_PREREQUISITES'), true, false)), [true, false, false],
    '只读用户可以展开与检查前置，但不显示暂停 / 恢复');
  assert.deepEqual(Object.values(capabilityGateActions(client, row('ACTIVE', 'SUSPENDED_MANUALLY'), true, false)), [true, false, false]);
});

test('AC-09 核对结果：解码严格校验，结果只在不早于当前合同版本时有效，有效结果覆盖行内执行状态', () => {
  assert.equal(commercialPaths.prerequisiteChecks(agreementId), `/commercial-admin/v1/agreements/${agreementId}/prerequisite-checks`);
  const result: PrerequisiteCheckResult = decodePrerequisiteCheckResult(agreementId)(response);
  assert.deepEqual(result.capabilities.map(item => [item.executionStatus, item.checks.length]), [['PENDING_PREREQUISITES', 3], [null, 0]]);
  assert.equal(result.capabilities[0].checks[1].reasonCode, null);
  assert.throws(() => decodePrerequisiteCheckResult('7319000000000000999')(response), ApiError, '合同 ID 与请求不一致');
  assert.throws(() => decodePrerequisiteCheckResult(agreementId)({ ...response, capabilities: [{ ...response.capabilities[0],
    checks: [{ code: 'RISK', result: 'SATISFIED', reasonCode: null, params: {} }] }] }), ApiError, '未知前置项失败关闭');
  assert.throws(() => decodePrerequisiteCheckResult(agreementId)({ ...response, capabilities: [{ ...response.capabilities[0],
    checks: [{ code: 'QUALIFICATION', result: 'UNSATISFIED', reasonCode: 'X', params: { subjectId: 7 } }] }] }), ApiError, '参数只能是字符串');
  const fresh = freshPrerequisites(result, 11);
  const capability = { agreementCapabilityId: '7319000000000000301', executionStatus: 'ENABLED' as const };
  assert.equal(effectiveExecutionStatus(capability, fresh), 'PENDING_PREREQUISITES');
  assert.equal(freshPrerequisites(result, 12).size, 0, '之后发生过写入的结果过期');
  assert.equal(freshPrerequisites(null, 1).size, 0);
  assert.equal(effectiveExecutionStatus(capability, freshPrerequisites(result, 12)), 'ENABLED');
});

test('AC-09 还差什么：逐项 ✔ / ✘ / ? / –，满足给满足说明、其余给 ICU 原因；不满足项带跳转；参数缺失时用页面上下文补齐，未知原因码显示原值', () => {
  const lines = prerequisiteLines(decodePrerequisiteCheckResult(agreementId)(response).capabilities[0].checks, context);
  assert.deepEqual(lines.map(line => [line.mark, line.title, line.result, line.detail, line.link]), [
    ['unsatisfied', 'Qualification', 'Not Satisfied', "Operating Entity SA001 isn't qualified yet.", { kind: 'entity', entityNo: 'SA001' }],
    ['satisfied', 'Agreement & Pricing', 'Satisfied', 'The agreement is in effect and the active price version covers this enablement.', null],
    ['skipped', 'Channel Identity', 'Not Checked', 'Channel identity will be checked once the price is set.', null]]);
  assert.deepEqual(lineCounts(lines), { satisfied: 2, unsatisfied: 1, unknown: 0 }, '暂不核对按满足计数');
  assert.equal(prerequisiteReason('CLIENT_NOT_QUALIFIED', {}, context), "Client 7300000000000000011 isn't qualified yet. Complete the Client's due diligence first.");
  assert.equal(prerequisiteReason('CLIENT_NOT_QUALIFIED', { subjectType: 'CLIENT', subjectId: '7300000000000000099' }, context),
    "Client 7300000000000000099 isn't qualified yet. Complete the Client's due diligence first.");
  assert.equal(prerequisiteReason('AGREEMENT_NOT_ACTIVE', { agreementStatus: 'TERMINATED' }, context), "The agreement isn't in effect (status: Terminated).");
  assert.equal(prerequisiteReason('TERM_VERSION_NOT_ACTIVE', {}, context), "There's no active Client Pricing term version for this agreement.");
  assert.equal(prerequisiteReason('NEW_REASON_CODE', {}, context), 'NEW_REASON_CODE');
  assert.equal(prerequisiteShortReason('PRICE_NOT_COVERED', {}, context), "Price doesn't cover it");
  assert.equal(prerequisiteShortReason('PRICE_NOT_COVERED', { currencies: 'SAR' }, context), "Price doesn't cover SAR", '原型「价格未覆盖 SAR」');
  assert.equal(prerequisiteShortReason('PRICE_NOT_COVERED', { currencies: 'AED,SAR' }, context), "Price doesn't cover AED, SAR");
  assert.equal(prerequisiteReason('PRICE_NOT_COVERED', { termType: 'CLIENT_PRICING', currencies: 'SAR' }, context),
    'The active Client Pricing term version has no price for SAR on this enablement.');
  assert.equal(prerequisiteReason('PRICE_NOT_COVERED', { termType: 'CLIENT_PRICING' }, context),
    "The active Client Pricing term version doesn't price every currency and condition of this enablement.", '没有币种参数时走原文案');
  assert.equal(prerequisiteShortReason('NEW_REASON_CODE', {}, context), 'NEW_REASON_CODE');
  const links = prerequisiteLines([
    { code: 'QUALIFICATION', result: 'UNSATISFIED', reasonCode: 'CLIENT_NOT_QUALIFIED', params: { subjectType: 'CLIENT', subjectId: '7300000000000000099' } },
    { code: 'AGREEMENT_TERM', result: 'UNSATISFIED', reasonCode: 'PRICE_NOT_COVERED', params: { termType: 'CLIENT_PRICING' } },
    { code: 'AGREEMENT_TERM', result: 'UNSATISFIED', reasonCode: 'AGREEMENT_NOT_ACTIVE', params: {} },
    { code: 'QUALIFICATION', result: 'UNKNOWN', reasonCode: 'CHECK_UNAVAILABLE', params: {} }], context);
  assert.deepEqual(links.map(line => line.link), [{ kind: 'client', clientId: '7300000000000000099' }, { kind: 'pricing' }, null, { kind: 'recheck' }]);
  assert.deepEqual([links[3].mark, links[3].result], ['unknown', 'Check Failed']);
});

test('REQ-02 渠道身份：五个渠道原因带 OE / 支付方式 / 币种 / MCC 并去配置 MID；Client 无 MCC 去 Client；等待价格无跳转；暂时无法核对为重新检查', () => {
  const channel = (result: 'UNSATISFIED' | 'SKIPPED' | 'UNKNOWN', reasonCode: string, params: Record<string, string>) =>
    ({ code: 'CHANNEL_IDENTITY' as const, result, reasonCode, params });
  const identity = { entityNo: 'AE001', paymentMethodCode: 'CARD', mccCode: '5999', currencies: 'USD' };
  const lines = prerequisiteLines([
    channel('UNSATISFIED', 'MID_NOT_FOUND', { ...identity, paymentMethodCode: 'APPLE_PAY' }),
    channel('UNSATISFIED', 'PAYMENT_METHOD_NOT_SUPPORTED', { ...identity, paymentMethodCode: 'APPLE_PAY' }),
    channel('UNSATISFIED', 'MCC_NOT_COVERED', { ...identity, mccCode: '5816' }),
    channel('UNSATISFIED', 'CURRENCY_NOT_SUPPORTED', { ...identity, currencies: 'EUR,GBP' }),
    channel('UNSATISFIED', 'PAYMENT_ACTION_NOT_SUPPORTED', { ...identity, mccCode: '5816' }),
    channel('UNSATISFIED', 'CLIENT_MCC_MISSING', { clientId: '7300000000000000099' }),
    channel('SKIPPED', 'AWAITING_PRICE', { entityNo: 'AE001' }),
    channel('UNKNOWN', 'CHECK_UNAVAILABLE', {})], { ...context, entityNo: 'SA001' });
  assert.deepEqual(lines.map(line => [line.mark, line.detail, line.link]), [
    ['unsatisfied', 'Operating Entity AE001 has no available MID.', { kind: 'mids', entityNo: 'AE001' }],
    ['unsatisfied', 'Operating Entity AE001 has no MID that supports APPLE_PAY (USD · MCC 5999).', { kind: 'mids', entityNo: 'AE001' }],
    ['unsatisfied', "AE001's MIDs that support CARD don't cover the Client's MCC 5816.", { kind: 'mids', entityNo: 'AE001' }],
    ['unsatisfied', "The price includes EUR, GBP, but AE001's MIDs for CARD · MCC 5999 don't accept EUR, GBP.", { kind: 'mids', entityNo: 'AE001' }],
    ['unsatisfied', "AE001's CARD · MCC 5816 capability doesn't enable sale or authorization.", { kind: 'mids', entityNo: 'AE001' }],
    ['unsatisfied', "Client 7300000000000000099's business classification (MCC) isn't confirmed yet.", { kind: 'client', clientId: '7300000000000000099' }],
    ['skipped', 'Channel identity will be checked once the price is set.', null],
    ['unknown', "Channel identity can't be checked right now; the system will retry automatically.", { kind: 'recheck' }]]);
  assert.deepEqual(lineCounts(lines), { satisfied: 1, unsatisfied: 6, unknown: 1 }, '暂时无法核对不计为缺口');
  assert.deepEqual(prerequisiteLines([channel('UNSATISFIED', 'MID_NOT_FOUND', {})], context)[0].link, { kind: 'mids', entityNo: 'AE001' },
    '没有 entityNo 参数时取开通项 OE');
  assert.equal(prerequisiteReason('PAYMENT_METHOD_NOT_SUPPORTED', { entityNo: 'AE001', paymentMethodCode: 'APPLE_PAY' }, context),
    "None of AE001's available MIDs support APPLE_PAY.", '没有币种参数时走不带币种的分支');
  assert.equal(prerequisiteReason('CHECK_UNAVAILABLE', {}, context, 'QUALIFICATION'),
    "The qualification service couldn't be reached; the status stays unchanged. Check again later.", '资格的检查失败文案不变');
  assert.deepEqual(['MID_NOT_FOUND', 'PAYMENT_METHOD_NOT_SUPPORTED', 'MCC_NOT_COVERED', 'CURRENCY_NOT_SUPPORTED', 'PAYMENT_ACTION_NOT_SUPPORTED',
    'CLIENT_MCC_MISSING', 'AWAITING_PRICE'].map(code => prerequisiteShortReason(code, identity, context)), [
    'AE001 has no MID', 'No MID supports CARD', "MID doesn't cover MCC 5999", "MID doesn't accept USD", 'MID payment action not enabled',
    'Client MCC not confirmed', 'Awaiting price']);
  assert.equal(prerequisiteShortReason('CHECK_UNAVAILABLE', {}, context, 'CHANNEL_IDENTITY'), 'Channel identity check unavailable');
  assert.equal(prerequisiteShortReason('CHECK_UNAVAILABLE', {}, context), 'Qualification check unavailable');
  assert.equal(prerequisiteReason('CHANNEL_IDENTITY_NOT_CONNECTED', {}, context), 'CHANNEL_IDENTITY_NOT_CONNECTED', '已删除的原因码不留文案键');
});

test('REQ-02 部分币种缺 MID：仍为满足（绿勾、计入满足），说明给提醒并可去配置 MID；其他满足项不带跳转', () => {
  const lines = prerequisiteLines([
    { code: 'QUALIFICATION', result: 'SATISFIED', reasonCode: null, params: {} },
    { code: 'CHANNEL_IDENTITY', result: 'SATISFIED', reasonCode: 'CHANNEL_IDENTITY_PARTIAL',
      params: { entityNo: 'AE001', paymentMethodCode: 'CARD', mccCode: '5999', currencies: 'EUR,GBP' } }], context);
  assert.deepEqual(lines.map(line => [line.mark, line.result, line.detail, line.link]), [
    ['satisfied', 'Satisfied', 'Client 7300000000000000011 and Operating Entity AE001 are qualified.', null],
    ['satisfied', 'Satisfied', 'Ready to accept payments; no MID under Operating Entity AE001 supports CARD (MCC 5999) in EUR, GBP, so orders in these currencies will fail.',
      { kind: 'mids', entityNo: 'AE001' }]]);
  assert.deepEqual(lineCounts(lines), { satisfied: 2, unsatisfied: 0, unknown: 0 }, '部分币种缺 MID 不计入未满足');
  assert.equal(prerequisiteShortReason('CHANNEL_IDENTITY_PARTIAL', { currencies: 'EUR,GBP' }, context), 'No MID for EUR, GBP');
});

const capability = (id: string, code: string, status: CapabilityStatus, executionStatus: CapabilityExecutionStatus | null) => ({
  agreementCapabilityId: id, entityNo: 'AE001', capabilityCode: code, status, executionStatus, effectiveFrom: '2026-10-20T00:00:00Z', effectiveTo: null,
  rollingReserveRate: null, rollingReserveDays: null, conditions: [], require3ds: false,
});

test('商务 · 14 区头摘要与开通项生效卡片：数量按执行状态（核对结果优先）；卡住原因取第一条不满足，价格类给去定价，手动暂停固定说明', () => {
  const capabilities = [capability('7319000000000000301', 'CARD_VISA', 'ACTIVE', 'PENDING_PREREQUISITES'),
    capability('7319000000000000302', 'CARD_MC', 'ACTIVE', 'ENABLED'),
    capability('7319000000000000303', 'APPLE_PAY', 'ACTIVE', 'ENABLED'),
    capability('7319000000000000304', 'GOOGLE_PAY', 'ACTIVE', 'SUSPENDED_MANUALLY'),
    capability('7319000000000000305', 'REFUND', 'DISABLED', null)];
  const fresh = freshPrerequisites({ agreementId, changeVersion: 9, capabilities: [
    { agreementCapabilityId: '7319000000000000301', entityNo: 'AE001', capabilityCode: 'CARD_VISA', capabilityKind: 'PAYMENT_METHOD', status: 'ACTIVE',
      executionStatus: 'PENDING_PREREQUISITES', checks: [{ code: 'AGREEMENT_TERM', result: 'UNSATISFIED', reasonCode: 'TERM_VERSION_NOT_ACTIVE', params: {} }] },
    { agreementCapabilityId: '7319000000000000303', entityNo: 'AE001', capabilityCode: 'APPLE_PAY', capabilityKind: 'PAYMENT_METHOD', status: 'ACTIVE',
      executionStatus: 'SUSPENDED_BY_PREREQUISITE', checks: [{ code: 'QUALIFICATION', result: 'SATISFIED', reasonCode: null, params: {} },
        { code: 'AGREEMENT_TERM', result: 'UNSATISFIED', reasonCode: 'PRICE_NOT_COVERED', params: { currencies: 'SAR' } }] }] }, 9);
  assert.deepEqual(sectionCounts(capabilities, fresh), { total: 5, enabled: 1, pending: 1, suspended: 2 });
  const agreement = { agreementType: 'CLIENT' as const, clientId: '7300000000000000011', status: 'ACTIVE' as const, capabilities };
  const blocked = blockedCapabilities(agreement, fresh);
  assert.deepEqual(blocked.map(item => [item.capabilityCode, item.text, item.needsPricing]), [
    ['CARD_VISA', 'CARD_VISA pending prerequisites: No active price version', true],
    ['APPLE_PAY', "APPLE_PAY suspended: Price doesn't cover SAR", true],
    ['GOOGLE_PAY', 'GOOGLE_PAY suspended manually; resume it to continue', false]]);
  assert.equal(blockedSummary(blocked.slice(0, 1)), 'CARD_VISA pending prerequisites: No active price version');
  assert.deepEqual(blockedCapabilities(agreement, new Map()).map(item => item.text)[0], 'CARD_VISA: Pending Prerequisites', '没有核对结果时只给状态');
  assert.deepEqual(blockedCapabilities({ ...agreement, agreementType: 'PARTNER' }, fresh), []);
  assert.equal(needsInitialCheck(agreement), true);
  assert.equal(needsInitialCheck({ agreementType: 'CLIENT', capabilities: capabilities.filter(item => item.executionStatus !== 'PENDING_PREREQUISITES') }), false,
    '只有已开通 / 手动暂停时进入页面不自动核对');
});

test('商务 · 16 恢复弹窗：从变更历史（最新在前）取该开通项最近一次手动暂停的操作人（含显示名）、时间与原因', () => {
  const entry = (recordId: string, reasonCode: string, data: Record<string, unknown>, createBy = '9001') => ({
    recordType: 'AGREEMENT_CAPABILITY', recordId, createBy, createByLabel: createBy === '9001' ? 'ops.zhang' : null, createTime: '2026-10-27T07:20:00Z', meta: { triggerType: 'COMMAND', triggerId: '1', reasonCode }, data });
  const entries = [entry('7319000000000000301', 'AGREEMENT_CAPABILITY_EXECUTION_CHANGED', {}, '0'),
    entry('7319000000000000302', 'AGREEMENT_CAPABILITY_SUSPENDED', { suspensionReason: 'other' }),
    entry('7319000000000000301', 'AGREEMENT_CAPABILITY_SUSPENDED', { suspensionReason: 'Domain renewal' }),
    entry('7319000000000000301', 'AGREEMENT_CAPABILITY_SUSPENDED', { suspensionReason: 'older' })];
  assert.deepEqual(lastSuspension(entries, '7319000000000000301'), { actorId: '9001', actorLabel: 'ops.zhang', time: '2026-10-27T07:20:00Z', reason: 'Domain renewal' });
  assert.equal(lastSuspension(entries, '7319000000000000399'), null);
  assert.equal(lastSuspension([entry('7319000000000000301', 'AGREEMENT_CAPABILITY_SUSPENDED', {})], '7319000000000000301')?.reason, null);
});
