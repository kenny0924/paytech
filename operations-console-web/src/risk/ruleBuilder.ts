import { prepareOperands } from './riskVariable.ts';
import type {
  AdditionalAction, ValueType, CreateRuleSetVersionRequest, OperatorCode, RuleConditionNode, RuleDefinition, RuleOperand, RuleSetDetail,
} from './riskApiModels.ts';
import type { AuthoringCatalog } from './riskAuthoringCatalog.ts';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog.ts';
import { translator } from '../i18n/index.ts';

const t = translator('risk');

export type EditorCondition =
  | { id: string; kind: 'ALL' | 'ANY' | 'NOT'; children: EditorCondition[] }
  | { id: string; kind: 'CONDITION'; fieldSide: 'LEFT' | 'RIGHT'; fieldCode: string; operator: string; values: string[]; operands?: RuleOperand[] };

export interface EditorRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  enabled: boolean;
  reasonCode: string;
  reasonMessage?: string;
  additionalActions?: EditorAdditionalAction[];
  condition: EditorCondition;
  actionCode: string;
  actionVersionNo: number;
}

export interface EditorAdditionalAction {
  id: string; actionCode: string; actionVersionNo: number; targetBindingCode: string; subjectVariableCode?: string | null;
  parameters: { parameterCode: string; valueType: ValueType; values: string[] }[];
}

export interface RuleSetEditorModel {
  pointCode: string;
  ruleSetCode: string;
  ruleSetName: string;
  changeReason: string;
  rules: EditorRule[];
}

export type RuleSetBase = { ruleSetVersionId: string; contentHash: string } | null;

const fields = new Map(CHECKOUT_PREPARE_AUTHORING_V1.fields.map(field => [field.fieldCode, field]));
const numberOperators = Object.freeze(CHECKOUT_PREPARE_AUTHORING_V1.operators.map(item => item.operator));
const stringOperators = Object.freeze(numberOperators.filter(operator => !['GT', 'GTE', 'LT', 'LTE', 'BETWEEN'].includes(operator)));
const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;

function fail(message: string): never { throw new Error(message); }
/** 终态动作在编辑器中的简称；未知动作回退为目录名称或编码。 */
export const terminalActionLabels: Record<string, string> = { ALLOW_CHECKOUT: 'PASS', REVIEW_CHECKOUT: 'REVIEW', DENY_CHECKOUT: 'REJECT', ALLOW_PAYMENT: 'PASS', REVIEW_PAYMENT: 'REVIEW', DENY_PAYMENT: 'REJECT' };
/** 拒绝类终态动作（如 DENY_CHECKOUT、DENY_PAYMENT）需要原因编码和拒绝文案。 */
export function isDenyAction(actionCode: string | null | undefined): boolean { return !!actionCode && actionCode.startsWith('DENY_'); }

function matches(value: unknown, pattern: RegExp): value is string {
  // A JavaScript $ can also match before a final newline; the complete token must match.
  return typeof value === 'string' && pattern.exec(value)?.[0] === value;
}

export function canonicalDecimal(value: string): string {
  if (!matches(value, /^-?(?:0|[1-9][0-9]{0,15})(?:\.[0-9]{1,8})?$/) || /^-0(?:\.0+)?$/.test(value)) {
    return fail(t('ruleBuilder.amountFormat'));
  }
  const [integer, fraction = ''] = value.split('.');
  return `${integer}.${fraction.padEnd(8, '0')}`;
}

function authoringField(fieldCode: string) {
  return fields.get(fieldCode) ?? fail(t('ruleBuilder.fieldUnsupported'));
}

export function operatorsForField(fieldCode: string): readonly OperatorCode[] {
  return authoringField(fieldCode).widget === 'DECIMAL' ? numberOperators : stringOperators;
}

export function canonicalFieldValue(fieldCode: string, value: string): string {
  const field = authoringField(fieldCode);
  if (typeof value !== 'string') return fail(t('ruleBuilder.valueText'));
  switch (field.widget) {
    case 'DECIMAL': return canonicalDecimal(value);
    case 'COUNTRY_CODE':
      if (!matches(value, /^[A-Z]{2}$/)) return fail(t('ruleBuilder.countryFormat'));
      break;
    case 'UPPER_CODE':
      if (!matches(value, codePattern)) return fail(t('ruleBuilder.industryFormat'));
      break;
    case 'MCC':
      if (!matches(value, /^[0-9]{4}$/)) return fail(t('ruleBuilder.mccFormat'));
      break;
    case 'CURRENCY':
      if (!matches(value, /^[A-Z]{3}$/)) return fail(t('ruleBuilder.currencyFormat'));
      break;
    case 'ENTITY_SOURCE':
      if (!['CLIENT', 'PARTNER'].includes(value)) return fail(t('ruleBuilder.entitySource'));
      break;
    case 'LOCALE': {
      const message = t('ruleBuilder.localeFormat');
      if (value.length === 0 || value.length > 512 || value.trim() !== value) return fail(message);
      try {
        const locales = Intl.getCanonicalLocales(value);
        if (locales.length === 1) return locales[0]!;
      } catch {
        // Intl errors may contain the input. Surface a fixed message without the literal.
      }
      return fail(message);
    }
  }
  return value;
}

type EditorGroup = Extract<EditorCondition, { children: EditorCondition[] }>;
type EditorPredicate = Extract<EditorCondition, { kind: 'CONDITION' }>;

function nodeToEditor(node: RuleConditionNode, variableFormat=false): EditorCondition {
  if (node.nodeType === 'GROUP') {
    const kinds = { AND: 'ALL', OR: 'ANY', NOT: 'NOT' } as const;
    return { id: node.nodeKey, kind: kinds[node.groupOperator!], children: [] };
  }
  if(variableFormat)return {id:node.nodeKey,kind:'CONDITION',fieldSide:'LEFT',fieldCode:'',operator:node.comparisonOperator!,values:[],operands:node.operands.map(o=>({...o}))};
  const context = node.operands.find(operand => operand.sourceType === 'CONTEXT')!;
  return { id: node.nodeKey, kind: 'CONDITION', fieldSide: context.operandRole, fieldCode: context.fieldCode!,
    operator: node.comparisonOperator!,
    values: node.operands.filter(operand => operand.sourceType === 'LITERAL').map(operand => String(operand.literalValue)),
  };
}

/** The response-local decoder has already proved the owner tree, roles and cardinalities. */
export function detailToEditor(detail: RuleSetDetail): RuleSetEditorModel {
  return {
    pointCode: detail.pointCode as RuleSetEditorModel['pointCode'], ruleSetCode: detail.ruleSetCode,
    ruleSetName: detail.ruleSetName, changeReason: detail.changeReason,
    rules: [...detail.rules].sort((left, right) => left.priorityNo - right.priorityNo).map(rule => {
      const nodes = new Map(rule.conditionNodes.map(node => [node.nodeKey, nodeToEditor(node,detail.operandFormat==='VARIABLE_REFERENCE') ]));
      const children = new Map<string, RuleConditionNode[]>();
      for (const node of rule.conditionNodes) if (node.parentNodeKey !== null) {
        const siblings = children.get(node.parentNodeKey) ?? [];
        siblings.push(node); children.set(node.parentNodeKey, siblings);
      }
      for (const [parentKey, siblings] of children) {
        (nodes.get(parentKey)! as EditorGroup).children = siblings.sort((left, right) => left.sequenceNo - right.sequenceNo)
          .map(node => nodes.get(node.nodeKey)!);
      }
      const root = rule.conditionNodes.find(node => node.parentNodeKey === null)!;
      return { id: rule.ruleCode, ruleCode: rule.ruleCode, ruleName: rule.ruleName, enabled: rule.enabledFlag,
        reasonCode: rule.reasonCode ?? '', condition: nodes.get(root.nodeKey)!,
        reasonMessage: rule.reasonMessage ?? '',
        additionalActions: rule.additionalActions.map(action => ({ id: crypto.randomUUID(), actionCode: action.actionCode,
          actionVersionNo: action.actionVersionNo, targetBindingCode: action.targetBindingCode, subjectVariableCode: action.subjectVariableCode,
          parameters: [...new Set(action.parameters.map(parameter => parameter.parameterCode))].map(parameterCode => {
            const parameters = action.parameters.filter(parameter => parameter.parameterCode === parameterCode);
            return { parameterCode, valueType: parameters[0]!.valueType, values: parameters.map(parameter => String(parameter.literalValue)) };
          }),
        })),
        actionCode: rule.action?.actionCode ?? '', actionVersionNo: rule.action?.actionVersionNo ?? 1,
      };
    }),
  };
}

function canonicalText(value: string, maximum: number, message: string): string {
  if (typeof value !== 'string') return fail(message);
  const normalized = value.normalize('NFC').trim();
  return normalized.length > 0 && normalized.length <= maximum ? normalized : fail(message);
}

function code(value: string): string {
  return matches(value, codePattern) ? value : fail(t('ruleBuilder.codeFormat'));
}

function predicateOperands(condition: EditorPredicate): { operator: OperatorCode; operands: RuleOperand[] } {
  if(condition.operands)return {operator:condition.operator as OperatorCode,operands:prepareOperands(condition.operator,condition.operands)};
  if (condition.fieldSide !== 'LEFT' && condition.fieldSide !== 'RIGHT') return fail(t('ruleBuilder.fieldSide'));
  const allowed = operatorsForField(condition.fieldCode);
  if (!(allowed as readonly string[]).includes(condition.operator)) return fail(t('ruleBuilder.operatorUnsupported'));
  const operator = condition.operator as OperatorCode;
  const nullary = operator === 'IS_NULL' || operator === 'IS_NOT_NULL';
  const set = operator === 'IN' || operator === 'NOT_IN';
  const values = condition.values;
  if (!Array.isArray(values) || (nullary ? values.length !== 0 : set ? values.length < 1 || values.length > 100
    : values.length !== (operator === 'BETWEEN' ? 2 : 1))) return fail(t('ruleBuilder.valueCount'));
  const valueType = authoringField(condition.fieldCode).widget === 'DECIMAL' ? 'NUMBER' : 'STRING';
  return { operator, operands: [
    { operandRole: condition.fieldSide, valueSequence: 1, sourceType: 'CONTEXT', fieldCode: condition.fieldCode, valueType, literalValue: null },
    ...values.map((value, index): RuleOperand => ({ operandRole: condition.fieldSide === 'LEFT' ? 'RIGHT' : 'LEFT',
      valueSequence: index + 1, sourceType: 'LITERAL', fieldCode: null, valueType,
      literalValue: canonicalFieldValue(condition.fieldCode, value) })),
  ] };
}

interface StructureBudget { nodes: number; operands: number; }
interface PendingCondition { condition: EditorCondition; parentNodeKey: string | null; sequenceNo: number; depth: number; }

function conditionToWire(condition: EditorCondition, budget: StructureBudget): RuleConditionNode[] {
  const nodes: RuleConditionNode[] = [];
  const pending: PendingCondition[] = [{ condition, parentNodeKey: null, sequenceNo: 1, depth: 1 }];
  // Check depth while walking, before traversing descendants; even an accidentally deep editor stays bounded.
  while (pending.length > 0) {
    const { condition: current, parentNodeKey, sequenceNo, depth } = pending.pop()!;
    if (depth > 12) return fail(t('ruleBuilder.maxDepth'));
    if (++budget.nodes > 2000) return fail(t('ruleBuilder.maxNodes'));
    const nodeKey = `n${nodes.length + 1}`;
    if (current.kind === 'CONDITION') {
      const { operator, operands } = predicateOperands(current);
      budget.operands += operands.length;
      if (budget.operands > 10000) return fail(t('ruleBuilder.maxOperands'));
      nodes.push({ nodeKey, parentNodeKey, sequenceNo, nodeType: 'PREDICATE', groupOperator: null,
        comparisonOperator: operator, operands });
      continue;
    }
    if (!['ALL', 'ANY', 'NOT'].includes(current.kind) || !Array.isArray(current.children)
      || (current.kind === 'NOT' ? current.children.length !== 1 : current.children.length < 2)) {
      return fail(t('ruleBuilder.groupChildren'));
    }
    if (budget.nodes + pending.length + current.children.length > 2000) return fail(t('ruleBuilder.maxNodes'));
    nodes.push({ nodeKey, parentNodeKey, sequenceNo, nodeType: 'GROUP', groupOperator: current.kind === 'ALL' ? 'AND' : current.kind === 'ANY' ? 'OR' : 'NOT',
      comparisonOperator: null, operands: [] });
    for (let index = current.children.length - 1; index >= 0; index--) {
      pending.push({ condition: current.children[index]!, parentNodeKey: nodeKey, sequenceNo: index + 1, depth: depth + 1 });
    }
  }
  return nodes;
}

export function editorToCreateRequest(editor: RuleSetEditorModel, base: RuleSetBase, catalog: AuthoringCatalog = CHECKOUT_PREPARE_AUTHORING_V1): CreateRuleSetVersionRequest {
  if (editor.pointCode !== 'CHECKOUT_PREPARE' && catalog.operandFormat !== 'VARIABLE_REFERENCE') return fail(t('ruleBuilder.variableFormatOnly'));
  if (base !== null && (!base || !matches(base.ruleSetVersionId, /^[1-9][0-9]{0,29}$/)
    || !matches(base.contentHash, /^[0-9a-f]{64}$/))) return fail(t('ruleBuilder.baseVersionInvalid'));
  if (!Array.isArray(editor.rules) || editor.rules.length < 1 || editor.rules.length > 200) return fail(t('ruleBuilder.ruleCount'));
  const ruleSetCode = code(editor.ruleSetCode);
  const ruleSetName = canonicalText(editor.ruleSetName, 255, t('ruleBuilder.ruleSetNameLength'));
  const changeReason = canonicalText(editor.changeReason, 500, t('ruleBuilder.changeReasonLength'));
  const seenCodes = new Set<string>();
  const budget: StructureBudget = { nodes: 0, operands: 0 };
  const rules = editor.rules.map((rule, index): RuleDefinition => {
    const ruleCode = code(rule.ruleCode);
    if (seenCodes.has(ruleCode)) return fail(t('ruleBuilder.duplicateRuleCode'));
    seenCodes.add(ruleCode);
    if (typeof rule.enabled !== 'boolean') return fail(t('ruleBuilder.enabledRequired'));
    if (rule.actionCode && (!catalog.actions.some(action => action.actionCode === rule.actionCode) || rule.actionVersionNo !== 1)) {
      return fail(t('ruleBuilder.actionUnsupported'));
    }
    if (typeof rule.reasonCode !== 'string') return fail(t('ruleBuilder.reasonCodeText'));
    const additionalActions = additionalToWire(rule.additionalActions ?? [], catalog);
    const reasonMessage = rule.reasonMessage?.trim() ? canonicalText(rule.reasonMessage, 500, t('ruleBuilder.reasonMessageLength')) : null;
    if (isDenyAction(rule.actionCode) && (!rule.reasonCode || !reasonMessage)) return fail(t('ruleBuilder.denyReasonRequired'));
    if (!isDenyAction(rule.actionCode) && reasonMessage) return fail(t('ruleBuilder.reasonMessageDenyOnly'));
    return { ruleCode, ruleName: canonicalText(rule.ruleName, 255, t('ruleBuilder.ruleNameLength')),
      priorityNo: (index + 1) * 10, enabledFlag: rule.enabled, reasonCode: rule.reasonCode.trim() === '' ? null : code(rule.reasonCode),
      conditionNodes: conditionToWire(rule.condition, budget), action: rule.actionCode ? { actionCode: rule.actionCode, actionVersionNo: rule.actionVersionNo } : null,
      reasonMessage, additionalActions,
    };
  });
  return { pointCode: editor.pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId: base?.ruleSetVersionId ?? null,
    baseContentHash: base?.contentHash ?? null, changeReason, rules, ...(catalog.operandFormat?{operandFormat:catalog.operandFormat}:{}) };
}

function additionalToWire(actions: readonly EditorAdditionalAction[], catalog: AuthoringCatalog): AdditionalAction[] {
  if (actions.length > 16) return fail(t('ruleBuilder.maxAdditionalActions'));
  return actions.map((action, index) => {
    const descriptor = catalog.additionalActions.find(item => item.actionCode === action.actionCode && item.versionNo === action.actionVersionNo);
    if (!descriptor || !catalog.targetBindings.some(target => target.bindingCode === action.targetBindingCode)) return fail(t('ruleBuilder.actionTargetUnsupported'));
    if(catalog.operandFormat&&!action.subjectVariableCode)return fail(t('ruleBuilder.subjectVariableRequired'));
    const definitions = catalog.actionParameters.filter(item => item.actionCode === action.actionCode && item.actionVersionNo === action.actionVersionNo && !item.targetParameterFlag);
    if (action.parameters.length !== definitions.length || new Set(action.parameters.map(parameter => parameter.parameterCode)).size !== definitions.length) return fail(t('ruleBuilder.actionParametersIncomplete'));
    const parameters = definitions.flatMap(definition => {
      const parameter = action.parameters.find(item => item.parameterCode === definition.parameterCode);
      if (!parameter || parameter.valueType !== definition.valueType || !parameter.values.length || parameter.values.length > definition.maxValues) return fail(t('ruleBuilder.actionParameters'));
      const values = parameter.values.map(value => {
        if (definition.valueType === 'DURATION') {
          if (!/^[1-9][0-9]*$/.test(value) || value.length > 30) return fail(t('ruleBuilder.cooldownFormat'));
          const duration = BigInt(value);
          if ((definition.minimumValue && duration < BigInt(definition.minimumValue)) || (definition.maximumValue && duration > BigInt(definition.maximumValue))) return fail(t('ruleBuilder.cooldownRange'));
          return value;
        }
        return code(value);
      });
      return [...new Set(values)].sort().map((literalValue, valueIndex) => ({ parameterCode: definition.parameterCode,
        valueSequence: valueIndex + 1, sourceType: 'LITERAL' as const, fieldCode: null, valueType: definition.valueType, literalValue }));
    });
    return { sequenceNo: index + 1, actionCode: action.actionCode, actionVersionNo: action.actionVersionNo, targetBindingCode: action.targetBindingCode, subjectVariableCode: action.subjectVariableCode, parameters };
  });
}
