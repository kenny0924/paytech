import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { commercialPaths, decodePlan } from './commercial.ts';
import { decodeTermPlanListItem, decodeTermVersionListItem } from './commercialConsole.ts';
import {
  decodeTemplateList, decodeVersionWorkspace, templateBody, templateChoices, templateLabel, templatePaths, templateRoutes, templateTermTypes,
} from './templates.ts';

const plan = { planId: '7319000000000000801', planCode: 'TPL-AE001-STD', templateName: '标准价 · AE001', termType: 'CLIENT_PRICING', entityNo: 'AE001',
  status: 'ACTIVE', changeVersion: '2', activeVersionId: '7319000000000000901', activeVersionNo: '1', latestVersionId: '7319000000000000902',
  latestVersionNo: 2, latestVersionStatus: 'PENDING_APPROVAL' };
const version = { versionId: '7319000000000000902', planId: plan.planId, termType: 'CLIENT_PRICING', agreementId: null, versionNo: '2', status: 'DRAFT',
  effectiveFrom: '2026-10-19T08:00:00Z', effectiveTo: null, roundingMode: 'HALF_UP', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', changeVersion: '5',
  ownerType: 'PLATFORM_TEMPLATE' };
const scope = { agreementId: '7319000000000000101', agreementNo: 'AGR-1', agreementName: '示例', agreementType: 'CLIENT', status: 'ACTIVE', clientId: '11',
  localPartnerId: null, entityNos: ['AE001'], capabilities: [] };

test('AC-04 模板专用路由：写与复核走 /commercial-terms/templates 与 /template-versions，与合同路由分开', () => {
  assert.equal(templatePaths.versions('5'), '/commercial-admin/v1/commercial-terms/templates/5/versions');
  assert.equal(templatePaths.prices('6'), '/commercial-admin/v1/commercial-terms/template-versions/6/prices');
  assert.equal(templatePaths.versionAction('6', 'approve'), '/commercial-admin/v1/commercial-terms/template-versions/6/approve');
  assert.equal(templateRoutes.version('6'), '/commercial/templates?versionId=6');
  assert.equal(templateTermTypes.includes('PARTNER_SHARE' as never), false, 'Partner 条款没有标准价目');
});

test('模板列表与版本读解码：兼容分页 / templates 两种外壳；版本读按 ownerType 区分标准价目与合同价目', () => {
  const [decoded] = decodeTemplateList({ items: [plan], page: 1, size: 100, total: 1 });
  assert.deepEqual([decoded.latestVersionNo, decoded.latestVersionStatus, decoded.activeVersionNo], ['2', 'PENDING_APPROVAL', '1']);
  assert.equal(decodeTemplateList({ templates: [{ ...plan, templateName: undefined, activeVersionId: null }] })[0].templateName, 'TPL-AE001-STD',
    '缺名称时用编号');
  assert.throws(() => decodeTemplateList({ items: [{ ...plan, entityNo: 'AE1' }] }), ApiError);
  assert.throws(() => decodeTemplateList([plan]), ApiError);
  const template = decodeVersionWorkspace(version.versionId)({ version, agreement: null, template: plan, prices: [] });
  assert.deepEqual([template.kind, template.workspace.version.ownerType, template.workspace.version.agreementId], ['TEMPLATE', 'PLATFORM_TEMPLATE', null]);
  assert.equal(template.kind === 'TEMPLATE' && template.workspace.template?.templateName, '标准价 · AE001', 'BFF template 摘要给出名称与 OE');
  assert.throws(() => decodeVersionWorkspace(version.versionId)({ version, agreement: null, template: { ...plan, planId: '9' }, prices: [] }), ApiError,
    '模板摘要必须属于该版本的方案');
  const price = { entityNo: 'SA001', currency: 'USD', pricingProfileCode: 'CARD_STANDARD', paymentMethodCode: 'CARD', priorityNo: 1, conditions: [],
    components: [{ componentNo: 1, componentCode: 'MDR', componentType: 'PERCENTAGE', triggerCode: 'CAPTURE', calculationBase: 'GROSS_AMOUNT',
      refundBehavior: 'RETURN_PRORATA', rate: '0.029' }], settlement: null };
  assert.throws(() => decodeVersionWorkspace(version.versionId)({ version, agreement: null, template: plan, prices: [price] }), ApiError, '价格只属于模板 OE');
  const legacy = decodeVersionWorkspace(version.versionId)({ version: { ...version, ownerType: undefined }, agreement: null, prices: [] });
  assert.equal(legacy.kind, 'TEMPLATE', '旧 BFF 无 ownerType 时按无合同识别');
  const contract = decodeVersionWorkspace(version.versionId)({ version: { ...version, ownerType: 'CLIENT', agreementId: scope.agreementId }, agreement: scope,
    prices: [] });
  assert.equal(contract.kind, 'AGREEMENT');
  assert.throws(() => decodeVersionWorkspace(version.versionId)({ version: { ...version, agreementId: '1' }, agreement: null, prices: [] }), ApiError,
    '模板版本不得带合同');
  assert.throws(() => decodeVersionWorkspace(version.versionId)({ version: { ...version, ownerType: 'OPERATOR' }, agreement: null, prices: [] }), ApiError);
  assert.throws(() => decodeVersionWorkspace('1')({ version, agreement: null, prices: [] }), ApiError);
  assert.equal(commercialPaths.version(version.versionId), `/commercial-admin/v1/commercial-terms/versions/${version.versionId}`, '模板读复用版本读');
  assert.equal(Object.hasOwn(templatePaths, 'version'), false, '不另设模板读路由');
});

test('C1 ownerType / templateName：方案详情、方案列表行与版本列表行', () => {
  const detail = decodePlan({ planId: plan.planId, planCode: plan.planCode, termType: 'CLIENT_PRICING', agreementId: null, entityNo: 'AE001', clientId: null,
    localPartnerId: null, status: 'ACTIVE', changeVersion: '2', ownerType: 'PLATFORM_TEMPLATE', templateName: '标准价 · AE001' });
  assert.deepEqual([detail.ownerType, detail.templateName], ['PLATFORM_TEMPLATE', '标准价 · AE001']);
  const planRow = decodeTermPlanListItem({ planId: plan.planId, planCode: plan.planCode, termType: 'CLIENT_PRICING', agreementId: null, status: 'ACTIVE',
    changeVersion: '2', createTime: '2026-10-19T08:00:00Z', ownerType: 'PLATFORM_TEMPLATE', templateName: '标准价 · AE001' });
  assert.deepEqual([planRow.ownerType, planRow.templateName], ['PLATFORM_TEMPLATE', '标准价 · AE001']);
  const versionRow = decodeTermVersionListItem({ ...version, planCode: plan.planCode, priceCount: 0, updateTime: '2026-10-19T08:00:00Z' });
  assert.equal(versionRow.ownerType, 'PLATFORM_TEMPLATE');
  assert.equal(decodeTermVersionListItem({ ...version, ownerType: undefined, planCode: 'P', priceCount: 0, updateTime: '2026-10-19T08:00:00Z' }).ownerType, null);
});

test('新建模板请求与可选模板：同 OE、同条款类型、已有版本', () => {
  assert.deepEqual(templateBody({ planCode: ' TPL-1 ', templateName: ' 优惠价 ', termType: 'CLIENT_PRICING', entityNo: ' ae001 ' }),
    { planCode: 'TPL-1', templateName: '优惠价', termType: 'CLIENT_PRICING', entityNo: 'AE001' });
  const plans = decodeTemplateList({ items: [plan, { ...plan, planId: '2', entityNo: 'SA001' }, { ...plan, planId: '3', termType: 'PLATFORM_FEE' },
    { ...plan, planId: '4', activeVersionId: null, latestVersionId: null }] });
  assert.deepEqual(templateChoices(plans, ['AE001'], 'CLIENT_PRICING').map(item => item.planId), [plan.planId]);
  assert.equal(templateLabel(plans[0], '3'), '标准价 · AE001 v3');
  assert.equal(templateLabel(plans[0]), '标准价 · AE001');
});
