import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { commercialPaths, decodeVersion, decodeWorkspace, pricesBody, priceDraft } from './commercial.ts';
import type { AgreementParty } from './commercial.ts';
import { decodeTermVersionListItem } from './commercialConsole.ts';
import {
  availableGenerationModes, basisRequired, belowStandardText, createVersionBody, defaultGenerationMode, fillFromTemplateBody, signatureBody,
  signatureIssues, signatureParties, signatureState, termVersionActions,
} from './versionFlow.ts';

const versionId = '7319000000000000601';
const agreementId = '7319000000000000101';
const version = { versionId, planId: '7319000000000000501', termType: 'CLIENT_PRICING', agreementId, versionNo: '2', status: 'APPROVED',
  effectiveFrom: '2026-10-19T08:00:00Z', effectiveTo: null, roundingMode: 'HALF_UP', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', changeVersion: '5' };
const party = (partyId: string, partyRole: AgreementParty['partyRole']): AgreementParty => ({ agreementPartyId: `${partyId}1`, partyId, partyProfileId: `${partyId}2`,
  partyRole, acceptanceMethod: null, acceptedByActorId: null, acceptedAt: null, acceptanceReference: null, evidenceDocumentId: null });
const all = { canManage: true, canApprove: true, canSign: true };

test('AC-08 版本流程：APPROVED 未签署不能排期、可登记签署；客户已签署或历史免签可排期；标准价目无签署', () => {
  assert.deepEqual([termVersionActions({ status: 'APPROVED', signatureExempt: false }, all).sign, termVersionActions({ status: 'APPROVED', signatureExempt: false }, all).schedule],
    [true, false]);
  assert.deepEqual([termVersionActions({ status: 'CLIENT_SIGNED', signatureExempt: false }, all).sign, termVersionActions({ status: 'CLIENT_SIGNED', signatureExempt: false }, all).schedule],
    [false, true]);
  assert.deepEqual([termVersionActions({ status: 'APPROVED', signatureExempt: true }, all).sign, termVersionActions({ status: 'APPROVED', signatureExempt: true }, all).schedule],
    [false, true], '历史免签版本不登记签署');
  const template = termVersionActions({ status: 'APPROVED', signatureExempt: false }, all, true);
  assert.deepEqual([template.sign, template.schedule, template.fillFromTemplate], [false, true, false]);
  assert.equal(termVersionActions({ status: 'APPROVED', signatureExempt: false }, { ...all, canSign: false }).sign, false, '签署需要签署权限');
  assert.equal(termVersionActions({ status: 'CLIENT_SIGNED', signatureExempt: false }, { ...all, canManage: false }).schedule, false, '排期需要价目维护');
  assert.equal(termVersionActions({ status: 'DRAFT', signatureExempt: false }, all).fillFromTemplate, true);
  assert.equal(termVersionActions({ status: 'PENDING_APPROVAL', signatureExempt: false }, all).fillFromTemplate, false);
});

test('签署状态与破标列：历史免签 / 客户已签署 / 待客户签署；破标数', () => {
  assert.equal(signatureState({ status: 'ACTIVE', signatureExempt: true }), 'EXEMPT');
  assert.equal(signatureState({ status: 'ACTIVE', signatureExempt: false, hasAcceptance: true }), 'SIGNED');
  assert.equal(signatureState({ status: 'CLIENT_SIGNED', signatureExempt: false }), 'SIGNED');
  assert.equal(signatureState({ status: 'APPROVED', signatureExempt: false }), 'PENDING');
  assert.equal(signatureState({ status: 'DRAFT', signatureExempt: false }), null);
  assert.deepEqual([belowStandardText(3), belowStandardText(0), belowStandardText(null)], ['破标 3', '—', null]);
});

test('AC-05 生成方式三选一：Partner 与模板自身不能基于模板，无来源不能复制；请求字段互斥', () => {
  assert.deepEqual(availableGenerationModes({ partner: false, template: false, hasPrevious: true }), ['COPY', 'TEMPLATE', 'BLANK']);
  assert.deepEqual(availableGenerationModes({ partner: true, template: false, hasPrevious: true }), ['COPY', 'BLANK']);
  assert.deepEqual(availableGenerationModes({ partner: false, template: true, hasPrevious: false }), ['BLANK']);
  assert.equal(defaultGenerationMode(['TEMPLATE', 'BLANK']), 'TEMPLATE');
  assert.equal(defaultGenerationMode(['COPY', 'TEMPLATE', 'BLANK']), 'COPY');
  const values = { copyFromVersionId: ' 11 ', sourceTemplateVersionId: ' 22 ', agreementId: '33', pricingModel: '', feeTaxTreatment: 'EXCLUSIVE' };
  assert.deepEqual(createVersionBody('5', { ...values, mode: 'TEMPLATE' }, false), { expectedChangeVersion: '5', copyFromVersionId: null,
    sourceTemplateVersionId: '22', agreementId: null, pricingModel: null, feeTaxTreatment: 'EXCLUSIVE' });
  assert.deepEqual(createVersionBody('5', { ...values, mode: 'COPY' }, true), { expectedChangeVersion: '5', copyFromVersionId: '11',
    sourceTemplateVersionId: null, agreementId: '33', pricingModel: null, feeTaxTreatment: 'EXCLUSIVE' });
  const blank = createVersionBody('5', { ...values, mode: 'BLANK', pricingModel: 'BLENDED' }, false);
  assert.deepEqual([blank.copyFromVersionId, blank.sourceTemplateVersionId, blank.pricingModel], [null, null, 'BLENDED']);
  assert.deepEqual([basisRequired('BLANK'), basisRequired('COPY'), basisRequired('TEMPLATE')], [true, false, false]);
  assert.deepEqual(fillFromTemplateBody('5', ' 22 '), { expectedChangeVersion: '5', templateVersionId: '22' });
  assert.equal(commercialPaths.fillFromTemplate(versionId), `/commercial-admin/v1/commercial-terms/versions/${versionId}/fill-from-template`);
});

test('AC-08 签署表单：签署方从合同 CLIENT / LOCAL_PARTNER 参与方带出；请求体规范化；前置检查', () => {
  const parties = [party('201', 'CLIENT'), party('201', 'OPERATING_ENTITY'), party('202', 'PLATFORM_SIGNATORY'), party('203', 'LOCAL_PARTNER'), party('201', 'CLIENT')];
  assert.deepEqual(signatureParties(parties, false).map(item => item.partyId), ['201']);
  assert.deepEqual(signatureParties(parties, true).map(item => item.partyId), ['203']);
  const values = { partyId: '201', acceptanceMethod: 'E_SIGNATURE' as const, acceptedAt: '2026-10-19T10:00:00Z', acceptanceReference: ' PSIGN-1 ',
    fileId: ' 7319000000000000701 ', contentHash: 'A'.repeat(64) };
  const body = signatureBody('5', values);
  assert.deepEqual([body.partyId, body.acceptanceReference, body.fileId, body.contentHash], ['201', 'PSIGN-1', '7319000000000000701', 'a'.repeat(64)]);
  assert.equal(Date.parse(body.acceptedAt), Date.parse(values.acceptedAt));
  assert.match(body.acceptedAt, /[+-]\d{2}:\d{2}$/, '带时区偏移');
  assert.equal(commercialPaths.versionAcceptance(versionId), `/commercial-admin/v1/commercial-terms/versions/${versionId}/acceptance`);
  assert.deepEqual(signatureIssues(values, signatureParties(parties, false)), []);
  assert.deepEqual(signatureIssues({ ...values, partyId: '202', contentHash: 'x', fileId: '0', acceptedAt: '2999-01-01T00:00:00Z' }, signatureParties(parties, false)),
    ['签署方必须是合同的签约参与方', '签署时刻不能晚于现在', '价格附件文件 ID 必须是正十进制整数', 'SHA-256 必须是 64 位十六进制']);
});

test('版本读模型新字段：破标数、来源、标准价已更新、免签、签署记录、相关性提示；缺省兼容写回执', () => {
  const plain = decodeVersion(versionId)(version);
  assert.deepEqual([plain.belowStandardCount, plain.sourceTemplateVersionId, plain.templateUpdate, plain.signatureExempt, plain.acceptance, plain.relevanceWarnings],
    [null, null, null, false, null, []]);
  const review = decodeVersion(versionId)({ ...version, status: 'CLIENT_SIGNED', belowStandardCount: 2, sourceTemplateVersionId: '7319000000000000901',
    templateUpdate: { latestTemplateVersionId: '7319000000000000902', latestVersionNo: 3 }, signatureExempt: false,
    acceptance: { versionAcceptanceId: '1', partyId: '201', acceptanceMethod: 'E_SIGNATURE', acceptedAt: '2026-10-19T10:00:00Z', acceptanceReference: 'PSIGN-1',
      fileId: '7', contentHash: 'a'.repeat(64), recordedBy: '9' },
    relevanceWarnings: [{ entityNo: 'AE001', currency: 'AED', pricingProfileCode: 'CARD_AMEX', reasonCode: 'CONDITION_OUT_OF_SCOPE', message: null }] });
  assert.equal(review.status, 'CLIENT_SIGNED');
  assert.deepEqual(review.templateUpdate, { latestTemplateVersionId: '7319000000000000902', latestVersionNo: '3' });
  assert.equal(review.acceptance?.acceptanceReference, 'PSIGN-1');
  assert.equal(review.relevanceWarnings[0].reasonCode, 'CONDITION_OUT_OF_SCOPE');
  assert.equal(decodeVersion()({ ...version, agreementId: null }).agreementId, null, '模板版本没有合同');
  assert.throws(() => decodeVersion()({ ...version, belowStandardCount: -1 }), ApiError);
  assert.throws(() => decodeVersion()({ ...version, acceptance: { partyId: '201' } }), ApiError);
  const row = decodeTermVersionListItem({ ...version, planCode: 'CP-1', priceCount: 3, updateTime: '2026-10-19T10:00:00Z', belowStandardCount: 1,
    signatureExempt: true, hasAcceptance: false });
  assert.deepEqual([row.belowStandardCount, row.signatureExempt, row.hasAcceptance], [1, true, false]);
  const legacy = decodeTermVersionListItem({ ...version, planCode: 'CP-1', priceCount: 3, updateTime: '2026-10-19T10:00:00Z' });
  assert.deepEqual([legacy.belowStandardCount, legacy.signatureExempt, legacy.hasAcceptance], [null, false, false]);
});

test('价格来源与破标：读回 sourceTemplatePriceId 与组件 benchmark，保存时回传来源', () => {
  const component = { componentId: '1', componentNo: 1, componentCode: 'MDR', componentType: 'PERCENTAGE', triggerCode: 'CAPTURE', calculationBase: 'GROSS_AMOUNT',
    refundBehavior: 'RETURN_PRORATA', rate: '0.025', fixedAmount: null, currency: null, minAmount: null, maxAmount: null, bandBasis: null, bandMode: null, bands: [],
    benchmark: { status: 'BELOW_STANDARD', standardRate: '0.029', standardFixedAmount: null, standardMinAmount: '0.50', standardMaxAmount: null } };
  const price = { entityNo: 'AE001', currency: 'AED', pricingProfileCode: 'CARD_VISA_DEBIT', paymentMethodCode: 'CARD', priorityNo: 1, priceId: '8',
    sourceTemplatePriceId: '7319000000000000911', conditions: [], components: [component], settlement: null };
  const workspace = decodeWorkspace(versionId)({ version: { ...version, status: 'DRAFT' }, prices: [price], agreement: { agreementId, agreementNo: 'AGR-1',
    agreementName: '示例', agreementType: 'CLIENT', status: 'ACTIVE', clientId: '11', localPartnerId: null, entityNos: ['AE001'], capabilities: [] } });
  assert.equal(workspace.prices[0].sourceTemplatePriceId, '7319000000000000911');
  assert.equal(workspace.prices[0].components[0].benchmark?.status, 'BELOW_STANDARD');
  assert.equal(pricesBody('5', workspace.prices.map(priceDraft)).prices[0].sourceTemplatePriceId, '7319000000000000911');
  assert.throws(() => decodeWorkspace(versionId)({ ...workspace, version: { ...version, status: 'DRAFT' },
    prices: [{ ...price, components: [{ ...component, benchmark: { status: 'LOWER' } }] }] }), ApiError);
});
