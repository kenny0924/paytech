/** 旧已保存配置的只读展示与精确旧合同；新建候选不使用此目录。 */
import { translator } from '../i18n/index.ts';
import { operatorLabel } from './riskVariable.ts';
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
/**
 * 显示文字在读取时由 `risk.legacyCatalog.*` 与 `risk.enum.operator.*` 翻译（getter），目录常量本身不存文案。
 */
const t = translator('risk');
const widgets: Record<string, AuthoringWidget> = {
  country_code: 'COUNTRY_CODE', locale: 'LOCALE', industry_code: 'UPPER_CODE', mcc_code: 'MCC', currency: 'CURRENCY',
  merchandise_amount: 'DECIMAL', discount_amount: 'DECIMAL', net_amount: 'DECIMAL', entity_source_type: 'ENTITY_SOURCE',
};
const displayFieldOrder = ['country_code', 'locale', 'industry_code', 'mcc_code', 'currency', 'merchandise_amount', 'discount_amount', 'net_amount', 'entity_source_type'];
const displayActions = ['ALLOW_CHECKOUT', 'REVIEW_CHECKOUT', 'DENY_CHECKOUT'];
const fieldDisplay = (fieldCode: string): AuthoringFieldDisplay => Object.freeze({ fieldCode, widget: widgets[fieldCode]!,
  get label() { return t(`legacyCatalog.field.${fieldCode}`); }, get helpText() { return t(`legacyCatalog.fieldHelp.${fieldCode}`); } });
export const CHECKOUT_PREPARE_AUTHORING_V1: AuthoringCatalog = Object.freeze({ descriptorCode: 'CHECKOUT_PREPARE_AUTHORING_V1',
  additionalActions: [], targetBindings: [], actionParameters: [],
  fields: Object.freeze(displayFieldOrder.map(fieldDisplay)),
  actions: Object.freeze(displayActions.map(actionCode => Object.freeze({ actionCode, get label() { return t(`legacyCatalog.action.${actionCode}`); } }))),
  operators: Object.freeze(operators.map(operator => Object.freeze({ operator, get label() { return operatorLabel(operator); } }))),
});

function incompatible(reason: string): AuthoringMatch { return { editable: false, reason }; }
export function matchAuthoringContext(context: RuleAuthoringContext): AuthoringMatch {
  const point = context.point;
  if (context.descriptorCode !== 'CHECKOUT_PREPARE_AUTHORING_V1' || point.pointCode !== 'CHECKOUT_PREPARE' || point.pointName !== 'Checkout Prepare' || point.businessDomain !== 'CHECKOUT' || point.inputContractCode !== 'ORDER_PREPARATION' || point.inputContractVersion !== 4 || point.outputContractCode !== 'RISK_DECISION_RESULT' || point.defaultDecision !== 'REVIEW' || point.evaluationStrategy !== 'FIRST_TERMINAL' || point.failureStrategy !== 'FAIL_CLOSED' || point.timeoutMillis !== 500 || point.status !== 'ACTIVE') return incompatible(t('legacyCatalog.incompatible.metadataVersion'));
  const conditionFields = context.fields.filter(field => field.ruleReferenceFlag);
  const terminalActions = context.actions.filter(action => action.terminalFlag);
  if (conditionFields.length !== wireFields.length || conditionFields.some((field, index) => field.fieldCode !== wireFields[index])) return incompatible(t('legacyCatalog.incompatible.fieldMetadata'));
  const expectedTypes: Record<string, ValueType> = { country_code: 'STRING', currency: 'STRING', discount_amount: 'NUMBER', entity_source_type: 'STRING', industry_code: 'STRING', locale: 'STRING', mcc_code: 'STRING', merchandise_amount: 'NUMBER', net_amount: 'NUMBER' };
  if (conditionFields.some(field => field.valueType !== expectedTypes[field.fieldCode] || !field.requiredFlag || field.dataClassification !== 'INTERNAL' || !field.ruleReferenceFlag || field.actionParameterFlag)) return incompatible(t('legacyCatalog.incompatible.fieldSemantics'));
  if (context.operators.length !== operators.length || context.operators.some((operator, index) => operator !== operators[index])) return incompatible(t('legacyCatalog.incompatible.operatorMetadata'));
  if (terminalActions.length !== wireActions.length || terminalActions.some((action, index) => action.actionCode !== wireActions[index] || action.versionNo !== 1 || action.actionCategory !== 'DECISION' || !action.terminalFlag || action.mergePolicy !== 'MOST_RESTRICTIVE' || !action.shadowSupportedFlag || action.status !== 'ACTIVE')) return incompatible(t('legacyCatalog.incompatible.actionMetadata'));
  const expectedNames: Record<string, readonly [string, string]> = { ALLOW_CHECKOUT: ['Allow Checkout', 'ALLOW'], DENY_CHECKOUT: ['Deny Checkout', 'DENY'], REVIEW_CHECKOUT: ['Review Checkout', 'REVIEW'] };
  if (terminalActions.some(action => action.actionName !== expectedNames[action.actionCode]?.[0] || action.decisionCode !== expectedNames[action.actionCode]?.[1])) return incompatible(t('legacyCatalog.incompatible.actionSemantics'));
  if (context.features.length || context.obligations.length || context.limitBindings.length) return incompatible(t('legacyCatalog.incompatible.browserUnsupported'));
  const additionalActions = context.actions.filter(action => !action.terminalFlag);
  if (additionalActions.some(action => action.status !== 'CONFIGURATION_ONLY' || action.decisionCode !== null || action.shadowSupportedFlag
    || !['RESTRICTION', 'TAG'].includes(action.actionCategory) || !['MAX', 'UNION'].includes(action.mergePolicy))) return incompatible(t('legacyCatalog.incompatible.additionalActionMetadata'));
  if (context.targetBindings.some(binding => !binding.requiredFields.length || binding.requiredFields.some(required =>
    !context.fields.some(field => field.fieldCode === required.fieldCode && field.requiredFlag && field.actionParameterFlag)))) return incompatible(t('legacyCatalog.incompatible.bindingFields'));
  if (context.actionParameters.some(parameter => !additionalActions.some(action => action.actionCode === parameter.actionCode && action.versionNo === parameter.actionVersionNo)
    || (parameter.targetParameterFlag ? parameter.allowedSourceType !== 'CONTEXT' : parameter.allowedSourceType !== 'LITERAL'
      || !['DURATION', 'STRING'].includes(parameter.valueType)))) return incompatible(t('legacyCatalog.incompatible.actionParameterMetadata'));
  if (additionalActions.some(action => !context.targetBindings.length || !context.actionParameters.some(parameter => parameter.actionCode === action.actionCode && parameter.actionVersionNo === action.versionNo && parameter.targetParameterFlag)
    || !context.actionParameters.some(parameter => parameter.actionCode === action.actionCode && parameter.actionVersionNo === action.versionNo && !parameter.targetParameterFlag))) return incompatible(t('legacyCatalog.incompatible.additionalActionIncomplete'));
  return { editable: true, catalog: Object.freeze({ ...CHECKOUT_PREPARE_AUTHORING_V1, additionalActions: Object.freeze(additionalActions.map(action => Object.freeze({ ...action }))), targetBindings: Object.freeze(context.targetBindings.map(target => Object.freeze({ ...target, requiredFields: Object.freeze(target.requiredFields.map(field => Object.freeze({ ...field }))) }))), actionParameters: Object.freeze(context.actionParameters.map(parameter => Object.freeze({ ...parameter }))) }) };
}
