import { prepareOperands } from './riskVariable.ts';
import type {
  AdditionalAction, ValueType, CreateRuleSetVersionRequest, OperatorCode, RuleConditionNode, RuleDefinition, RuleOperand, RuleSetDetail,
} from './riskApiModels.ts';
import type { AuthoringCatalog } from './riskAuthoringCatalog.ts';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog.ts';

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
  pointCode: 'CHECKOUT_PREPARE';
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

function matches(value: unknown, pattern: RegExp): value is string {
  // A JavaScript $ can also match before a final newline; the complete token must match.
  return typeof value === 'string' && pattern.exec(value)?.[0] === value;
}

export function canonicalDecimal(value: string): string {
  if (!matches(value, /^-?(?:0|[1-9][0-9]{0,15})(?:\.[0-9]{1,8})?$/) || /^-0(?:\.0+)?$/.test(value)) {
    return fail('金额须为最多 16 位整数、8 位小数的十进制文本，不能使用负零。');
  }
  const [integer, fraction = ''] = value.split('.');
  return `${integer}.${fraction.padEnd(8, '0')}`;
}

function authoringField(fieldCode: string) {
  return fields.get(fieldCode) ?? fail('请选择当前版本支持的字段。');
}

export function operatorsForField(fieldCode: string): readonly OperatorCode[] {
  return authoringField(fieldCode).widget === 'DECIMAL' ? numberOperators : stringOperators;
}

export function canonicalFieldValue(fieldCode: string, value: string): string {
  const field = authoringField(fieldCode);
  if (typeof value !== 'string') return fail('条件值必须使用文本。');
  switch (field.widget) {
    case 'DECIMAL': return canonicalDecimal(value);
    case 'COUNTRY_CODE':
      if (!matches(value, /^[A-Z]{2}$/)) return fail('国家/地区须为两位大写字母。');
      break;
    case 'UPPER_CODE':
      if (!matches(value, codePattern)) return fail('行业代码须为 1–64 位大写字母、数字或下划线，并以字母开头。');
      break;
    case 'MCC':
      if (!matches(value, /^[0-9]{4}$/)) return fail('MCC 须为四位数字。');
      break;
    case 'CURRENCY':
      if (!matches(value, /^[A-Z]{3}$/)) return fail('币种须为三位大写字母。');
      break;
    case 'ENTITY_SOURCE':
      if (!['PLATFORM', 'CLIENT', 'PARTNER'].includes(value)) return fail('请选择有效的经营主体来源。');
      break;
    case 'LOCALE': {
      const message = '语言区域须为一个有效的 BCP 47 标签。';
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
  return matches(value, codePattern) ? value : fail('编码须为 1–64 位大写字母、数字或下划线，并以字母开头。');
}

function predicateOperands(condition: EditorPredicate): { operator: OperatorCode; operands: RuleOperand[] } {
  if(condition.operands)return {operator:condition.operator as OperatorCode,operands:prepareOperands(condition.operator,condition.operands)};
  if (condition.fieldSide !== 'LEFT' && condition.fieldSide !== 'RIGHT') return fail('请选择条件字段所在的比较方向。');
  const allowed = operatorsForField(condition.fieldCode);
  if (!(allowed as readonly string[]).includes(condition.operator)) return fail('该字段不支持所选运算符。');
  const operator = condition.operator as OperatorCode;
  const nullary = operator === 'IS_NULL' || operator === 'IS_NOT_NULL';
  const set = operator === 'IN' || operator === 'NOT_IN';
  const values = condition.values;
  if (!Array.isArray(values) || (nullary ? values.length !== 0 : set ? values.length < 1 || values.length > 100
    : values.length !== (operator === 'BETWEEN' ? 2 : 1))) return fail('条件值数量不符合所选运算符要求。');
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
    if (depth > 12) return fail('条件嵌套最多支持 12 层。');
    if (++budget.nodes > 2000) return fail('每个规则集最多支持 2,000 个条件和条件组。');
    const nodeKey = `n${nodes.length + 1}`;
    if (current.kind === 'CONDITION') {
      const { operator, operands } = predicateOperands(current);
      budget.operands += operands.length;
      if (budget.operands > 10000) return fail('每个规则集最多支持 10,000 个字段和值。');
      nodes.push({ nodeKey, parentNodeKey, sequenceNo, nodeType: 'PREDICATE', groupOperator: null,
        comparisonOperator: operator, operands });
      continue;
    }
    if (!['ALL', 'ANY', 'NOT'].includes(current.kind) || !Array.isArray(current.children)
      || (current.kind === 'NOT' ? current.children.length !== 1 : current.children.length < 2)) {
      return fail('满足全部/任一至少需要两个子条件，不满足需要恰好一个子条件。');
    }
    if (budget.nodes + pending.length + current.children.length > 2000) return fail('每个规则集最多支持 2,000 个条件和条件组。');
    nodes.push({ nodeKey, parentNodeKey, sequenceNo, nodeType: 'GROUP', groupOperator: current.kind === 'ALL' ? 'AND' : current.kind === 'ANY' ? 'OR' : 'NOT',
      comparisonOperator: null, operands: [] });
    for (let index = current.children.length - 1; index >= 0; index--) {
      pending.push({ condition: current.children[index]!, parentNodeKey: nodeKey, sequenceNo: index + 1, depth: depth + 1 });
    }
  }
  return nodes;
}

export function editorToCreateRequest(editor: RuleSetEditorModel, base: RuleSetBase, catalog: AuthoringCatalog = CHECKOUT_PREPARE_AUTHORING_V1): CreateRuleSetVersionRequest {
  if (editor.pointCode !== 'CHECKOUT_PREPARE') return fail('当前仅支持结账准备规则。');
  if (base !== null && (!base || !matches(base.ruleSetVersionId, /^[1-9][0-9]{0,29}$/)
    || !matches(base.contentHash, /^[0-9a-f]{64}$/))) return fail('基础版本信息无效，请重新读取版本。');
  if (!Array.isArray(editor.rules) || editor.rules.length < 1 || editor.rules.length > 200) return fail('规则集须包含 1–200 条规则。');
  const ruleSetCode = code(editor.ruleSetCode);
  const ruleSetName = canonicalText(editor.ruleSetName, 255, '规则集名称须为 1–255 个字符。');
  const changeReason = canonicalText(editor.changeReason, 500, '变更原因须为 1–500 个字符。');
  const seenCodes = new Set<string>();
  const budget: StructureBudget = { nodes: 0, operands: 0 };
  const rules = editor.rules.map((rule, index): RuleDefinition => {
    const ruleCode = code(rule.ruleCode);
    if (seenCodes.has(ruleCode)) return fail('同一规则集中的规则编码不能重复。');
    seenCodes.add(ruleCode);
    if (typeof rule.enabled !== 'boolean') return fail('请选择规则启用状态。');
    if (rule.actionCode && (!catalog.actions.some(action => action.actionCode === rule.actionCode) || rule.actionVersionNo !== 1)) {
      return fail('请选择当前版本支持的规则动作。');
    }
    if (typeof rule.reasonCode !== 'string') return fail('原因编码必须使用文本。');
    const additionalActions = additionalToWire(rule.additionalActions ?? [], catalog);
    const reasonMessage = rule.reasonMessage?.trim() ? canonicalText(rule.reasonMessage, 500, '拒绝文案须为 1–500 个字符。') : null;
    if (rule.actionCode === 'DENY_CHECKOUT' && (!rule.reasonCode || !reasonMessage)) return fail('拒绝规则须手工填写原因编码和拒绝文案。');
    if (rule.actionCode !== 'DENY_CHECKOUT' && reasonMessage) return fail('只有拒绝规则可以填写拒绝文案。');
    return { ruleCode, ruleName: canonicalText(rule.ruleName, 255, '规则名称须为 1–255 个字符。'),
      priorityNo: (index + 1) * 10, enabledFlag: rule.enabled, reasonCode: rule.reasonCode.trim() === '' ? null : code(rule.reasonCode),
      conditionNodes: conditionToWire(rule.condition, budget), action: rule.actionCode ? { actionCode: rule.actionCode, actionVersionNo: rule.actionVersionNo } : null,
      reasonMessage, additionalActions,
    };
  });
  return { pointCode: editor.pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId: base?.ruleSetVersionId ?? null,
    baseContentHash: base?.contentHash ?? null, changeReason, rules, ...(catalog.operandFormat?{operandFormat:catalog.operandFormat}:{}) };
}

function additionalToWire(actions: readonly EditorAdditionalAction[], catalog: AuthoringCatalog): AdditionalAction[] {
  if (actions.length > 16) return fail('每条规则最多支持 16 个附加动作。');
  return actions.map((action, index) => {
    const descriptor = catalog.additionalActions.find(item => item.actionCode === action.actionCode && item.versionNo === action.actionVersionNo);
    if (!descriptor || !catalog.targetBindings.some(target => target.bindingCode === action.targetBindingCode)) return fail('请选择当前决策点支持的动作和主体。');
    if(catalog.operandFormat&&!action.subjectVariableCode)return fail('请选择动作主体变量。');
    const definitions = catalog.actionParameters.filter(item => item.actionCode === action.actionCode && item.actionVersionNo === action.actionVersionNo && !item.targetParameterFlag);
    if (action.parameters.length !== definitions.length || new Set(action.parameters.map(parameter => parameter.parameterCode)).size !== definitions.length) return fail('附加动作参数不完整。');
    const parameters = definitions.flatMap(definition => {
      const parameter = action.parameters.find(item => item.parameterCode === definition.parameterCode);
      if (!parameter || parameter.valueType !== definition.valueType || !parameter.values.length || parameter.values.length > definition.maxValues) return fail('请按动作定义填写参数。');
      const values = parameter.values.map(value => {
        if (definition.valueType === 'DURATION') {
          if (!/^[1-9][0-9]*$/.test(value) || value.length > 30) return fail('冷却时长须为正整数字符串（毫秒）。');
          const duration = BigInt(value);
          if ((definition.minimumValue && duration < BigInt(definition.minimumValue)) || (definition.maximumValue && duration > BigInt(definition.maximumValue))) return fail('冷却时长超出 Risk 定义的范围。');
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
