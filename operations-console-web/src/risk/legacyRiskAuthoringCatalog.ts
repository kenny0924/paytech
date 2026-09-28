/** 旧已保存配置的只读展示与精确旧合同；新建候选不使用此目录。 */
import type { ActionParameterDescriptor, DecisionActionDescriptor, TargetBindingDescriptor, OperatorCode, RuleAuthoringContext, ValueType } from './riskApiModels.ts';

export type AuthoringWidget = 'COUNTRY_CODE' | 'LOCALE' | 'UPPER_CODE' | 'MCC' | 'CURRENCY' | 'DECIMAL' | 'ENTITY_SOURCE';
export interface AuthoringFieldDisplay { fieldCode: string; label: string; helpText: string; widget: AuthoringWidget; }
export interface AuthoringActionDisplay { actionCode: string; label: string; }
export interface AuthoringOperatorDisplay { operator: OperatorCode; label: string; }
export interface AuthoringCatalog { descriptorCode: string; fields: readonly AuthoringFieldDisplay[]; actions: readonly AuthoringActionDisplay[]; operators: readonly AuthoringOperatorDisplay[]; additionalActions: readonly DecisionActionDescriptor[]; targetBindings: readonly TargetBindingDescriptor[]; actionParameters: readonly ActionParameterDescriptor[]; }
export type AuthoringMatch = { editable: true; catalog: AuthoringCatalog } | { editable: false; reason: string };

const wireFields = ['country_code', 'currency', 'discount_amount', 'entity_source_type', 'industry_code', 'locale', 'mcc_code', 'merchandise_amount', 'net_amount'] as const;
const wireActions = ['ALLOW_CHECKOUT', 'DENY_CHECKOUT', 'REVIEW_CHECKOUT'] as const;
const operators = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL'] as const;
const displays: Record<string, Omit<AuthoringFieldDisplay, 'fieldCode'>> = {
  country_code: { label: '国家/地区', helpText: 'ISO 3166-1 大写二字母，例如 AE', widget: 'COUNTRY_CODE' },
  locale: { label: '语言区域', helpText: '规范 BCP 47 标签，例如 ar-AE', widget: 'LOCALE' },
  industry_code: { label: '行业代码', helpText: '1–64 位平台行业大写代码', widget: 'UPPER_CODE' },
  mcc_code: { label: 'MCC', helpText: '保留前导零的四位十进制代码', widget: 'MCC' },
  currency: { label: '币种', helpText: 'ISO 4217 大写三字母，例如 USD', widget: 'CURRENCY' },
  merchandise_amount: { label: '商品金额', helpText: '最多 16 位整数和 8 位小数', widget: 'DECIMAL' },
  discount_amount: { label: '优惠金额', helpText: '最多 16 位整数和 8 位小数', widget: 'DECIMAL' },
  net_amount: { label: '净金额', helpText: '最多 16 位整数和 8 位小数', widget: 'DECIMAL' },
  entity_source_type: { label: '经营主体来源', helpText: '仅 PLATFORM、CLIENT 或 PARTNER', widget: 'ENTITY_SOURCE' },
};
const displayFieldOrder = ['country_code', 'locale', 'industry_code', 'mcc_code', 'currency', 'merchandise_amount', 'discount_amount', 'net_amount', 'entity_source_type'];
const displayActions = [{ actionCode: 'ALLOW_CHECKOUT', label: '通过（PASS）' }, { actionCode: 'REVIEW_CHECKOUT', label: '复核（REVIEW）' }, { actionCode: 'DENY_CHECKOUT', label: '拒绝（REJECT）' }];
const operatorLabels = ['等于', '不等于', '大于', '大于等于', '小于', '小于等于', '属于', '不属于', '介于', '为空', '不为空'];
export const CHECKOUT_PREPARE_AUTHORING_V1: AuthoringCatalog = Object.freeze({ descriptorCode: 'CHECKOUT_PREPARE_AUTHORING_V1',
  additionalActions: [], targetBindings: [], actionParameters: [],
  fields: Object.freeze(displayFieldOrder.map(fieldCode => Object.freeze({ fieldCode, ...displays[fieldCode]! }))),
  actions: Object.freeze(displayActions.map(action => Object.freeze(action))),
  operators: Object.freeze(operators.map((operator, index) => Object.freeze({ operator, label: operatorLabels[index]! }))),
});

function incompatible(reason: string): AuthoringMatch { return { editable: false, reason }; }
export function matchAuthoringContext(context: RuleAuthoringContext): AuthoringMatch {
  const point = context.point;
  if (context.descriptorCode !== 'CHECKOUT_PREPARE_AUTHORING_V1' || point.pointCode !== 'CHECKOUT_PREPARE' || point.pointName !== 'Checkout Prepare' || point.businessDomain !== 'CHECKOUT' || point.inputContractCode !== 'ORDER_PREPARATION' || point.inputContractVersion !== 4 || point.outputContractCode !== 'RISK_DECISION_RESULT' || point.defaultDecision !== 'REVIEW' || point.evaluationStrategy !== 'FIRST_TERMINAL' || point.failureStrategy !== 'FAIL_CLOSED' || point.timeoutMillis !== 500 || point.status !== 'ACTIVE') return incompatible('当前元数据版本不兼容。');
  const conditionFields = context.fields.filter(field => field.ruleReferenceFlag);
  const terminalActions = context.actions.filter(action => action.terminalFlag);
  if (conditionFields.length !== wireFields.length || conditionFields.some((field, index) => field.fieldCode !== wireFields[index])) return incompatible('字段元数据不兼容。');
  const expectedTypes: Record<string, ValueType> = { country_code: 'STRING', currency: 'STRING', discount_amount: 'NUMBER', entity_source_type: 'STRING', industry_code: 'STRING', locale: 'STRING', mcc_code: 'STRING', merchandise_amount: 'NUMBER', net_amount: 'NUMBER' };
  if (conditionFields.some(field => field.valueType !== expectedTypes[field.fieldCode] || !field.requiredFlag || field.dataClassification !== 'INTERNAL' || !field.ruleReferenceFlag || field.actionParameterFlag)) return incompatible('字段语义不兼容。');
  if (context.operators.length !== operators.length || context.operators.some((operator, index) => operator !== operators[index])) return incompatible('运算符元数据不兼容。');
  if (terminalActions.length !== wireActions.length || terminalActions.some((action, index) => action.actionCode !== wireActions[index] || action.versionNo !== 1 || action.actionCategory !== 'DECISION' || !action.terminalFlag || action.mergePolicy !== 'MOST_RESTRICTIVE' || !action.shadowSupportedFlag || action.status !== 'ACTIVE')) return incompatible('动作元数据不兼容。');
  const expectedNames: Record<string, readonly [string, string]> = { ALLOW_CHECKOUT: ['Allow Checkout', 'ALLOW'], DENY_CHECKOUT: ['Deny Checkout', 'DENY'], REVIEW_CHECKOUT: ['Review Checkout', 'REVIEW'] };
  if (terminalActions.some(action => action.actionName !== expectedNames[action.actionCode]?.[0] || action.decisionCode !== expectedNames[action.actionCode]?.[1])) return incompatible('动作语义不兼容。');
  if (context.features.length || context.obligations.length || context.limitBindings.length) return incompatible('当前元数据包含浏览器不支持的能力。');
  const additionalActions = context.actions.filter(action => !action.terminalFlag);
  if (additionalActions.some(action => action.status !== 'CONFIGURATION_ONLY' || action.decisionCode !== null || action.shadowSupportedFlag
    || !['RESTRICTION', 'TAG'].includes(action.actionCategory) || !['MAX', 'UNION'].includes(action.mergePolicy))) return incompatible('附加动作元数据不兼容。');
  if (context.targetBindings.some(binding => !binding.requiredFields.length || binding.requiredFields.some(required =>
    !context.fields.some(field => field.fieldCode === required.fieldCode && field.requiredFlag && field.actionParameterFlag)))) return incompatible('主体绑定缺少输入字段。');
  if (context.actionParameters.some(parameter => !additionalActions.some(action => action.actionCode === parameter.actionCode && action.versionNo === parameter.actionVersionNo)
    || (parameter.targetParameterFlag ? parameter.allowedSourceType !== 'CONTEXT' : parameter.allowedSourceType !== 'LITERAL'
      || !['DURATION', 'STRING'].includes(parameter.valueType)))) return incompatible('动作参数元数据不兼容。');
  if (additionalActions.some(action => !context.targetBindings.length || !context.actionParameters.some(parameter => parameter.actionCode === action.actionCode && parameter.actionVersionNo === action.versionNo && parameter.targetParameterFlag)
    || !context.actionParameters.some(parameter => parameter.actionCode === action.actionCode && parameter.actionVersionNo === action.versionNo && !parameter.targetParameterFlag))) return incompatible('附加动作定义不完整。');
  return { editable: true, catalog: Object.freeze({ ...CHECKOUT_PREPARE_AUTHORING_V1, additionalActions: Object.freeze(additionalActions.map(action => Object.freeze({ ...action }))), targetBindings: Object.freeze(context.targetBindings.map(target => Object.freeze({ ...target, requiredFields: Object.freeze(target.requiredFields.map(field => Object.freeze({ ...field }))) }))), actionParameters: Object.freeze(context.actionParameters.map(parameter => Object.freeze({ ...parameter }))) }) };
}
