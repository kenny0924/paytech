import { invariant } from './riskDictionary.ts';
import { nodeTypeLabel, paymentOutputFallback } from './decisionFlow.ts';
import { operatorLabel as variableOperatorLabel, variableSourceLabel, variableTypeLabel } from './riskVariable.ts';
import { enumLabel, tOr, translator } from '../i18n/index.ts';

const t = translator('risk');
export { nodeTypeLabel, variableSourceLabel, variableTypeLabel };

/** 评估查询方式：按支付尝试 ID 或订单 ID 查正式评估，或直接按评估 ID 查。 */
export type EvaluationQueryType = 'attempt' | 'order' | 'evaluation';
export type EvaluationResult = 'ALLOW' | 'REVIEW' | 'DENY' | 'FAILED' | 'TIMED_OUT';
export type PredicateResult = boolean | null;

/** 评估结论（Risk RCO-I06 结论字段原样透传）。 */
export interface Evaluation {
  evaluationId: string; pointCode: string; businessKey: string; mode: string; result: string;
  reasonCode: string | null; reasonMessage: string | null; outputs: Record<string, string>; failureCode: string | null;
  releaseId: string; environment: string; flowId: string; flowCode: string; flowVersionId: string; flowVersionNo: number;
  evaluationTime: string; elapsedMillis: number; baselineEvaluationId: string | null; shadowRunId: string | null; pathAvailable: boolean;
}
export interface PathStep {
  stepNo: number; nodeKey: string; nodeType: string; nodeName: string | null; ruleSetVersionId: string | null; ruleSetCode: string | null;
  ruleSetVersionNo: number | null; matched: boolean | null; decision: string | null; reused: boolean | null; exitEdgeKey: string | null; exitEdgeName: string | null;
}
export interface ConditionOperand {
  operandRole: string; sourceType: string; valueType: string | null; variableCode: string | null; variableName: string | null;
  literalValue: string | null; numericUnit: string | null; currencyCode: string | null; currencyVariableCode: string | null;
}
/** BFF 用规则集版本详情组装的条件树；result 为 null 表示未求值。 */
export interface ConditionNode {
  nodeKey: string; nodeType: 'GROUP' | 'PREDICATE'; groupOperator: string | null; comparisonOperator: string | null;
  operands: ConditionOperand[]; result: PredicateResult; children: ConditionNode[];
}
export interface RuleItem {
  ruleSetVersionId: string; ruleId: string; ruleCode: string; ruleName: string; priorityNo: number;
  matched: boolean; terminal: boolean; action: string | null; conditionAvailable: boolean; condition: ConditionNode | null;
}
export interface VariableItem {
  variableCode: string; variableName: string | null; sourceType: string; valueType: string; value: string | null; values: string[] | null;
  valueBasis: string; technicalStatus: string | null; failureCode: string | null; elapsedMillis: number | null;
}
export interface ShadowItem { evaluationId: string; shadowRunId: string; result: string; decisionChanged: boolean; outputChanged: boolean; severity: string | null }
export interface EvaluationExplanation {
  evaluationId: string; inputs: Record<string, string | null>; pathAvailable: boolean; path: PathStep[];
  rules: RuleItem[]; variables: VariableItem[]; shadows: ShadowItem[];
}

const MAX_ID = 9223372036854775807n;
const MAX_CONDITION_DEPTH = 32;

/* ---------- 响应解码：校验页面用到的字段；可空字段缺省与 null 等价，未知字段忽略 ---------- */
type Row = Record<string, unknown>;
function record(v: unknown): Row { invariant(v !== null && typeof v === 'object' && !Array.isArray(v)); return v as Row; }
function id(v: unknown): string { invariant(typeof v === 'string' && /^[1-9]\d{0,18}$/.test(v) && BigInt(v) <= MAX_ID); return v; }
function str(v: unknown, max = 512): string { invariant(typeof v === 'string' && v.length > 0 && [...v].length <= max); return v; }
function code(v: unknown): string { const s = str(v, 64); invariant(/^[A-Z][A-Z0-9_]{0,63}$/.test(s)); return s; }
function int(v: unknown): number { invariant(typeof v === 'number' && Number.isSafeInteger(v) && v >= 0); return v; }
function bool(v: unknown): boolean { invariant(typeof v === 'boolean'); return v; }
function opt<T>(v: unknown, decode: (v: unknown) => T): T | null { return v === null || v === undefined ? null : decode(v); }
function list<T>(v: unknown, decode: (v: unknown) => T, max = 1000): T[] { invariant(Array.isArray(v) && v.length <= max); return v.map(decode); }
function stringMap(v: unknown): Record<string, string> {
  const r = record(v); const out: Record<string, string> = {};
  for (const [k, value] of Object.entries(r)) { invariant(typeof value === 'string'); out[k] = value; }
  return out;
}

export function decodeEvaluation(v: unknown): Evaluation {
  const r = record(v);
  return {
    evaluationId: id(r.evaluationId), pointCode: code(r.pointCode), businessKey: str(r.businessKey, 128), mode: code(r.mode), result: code(r.result),
    reasonCode: opt(r.reasonCode, v => str(v, 128)), reasonMessage: opt(r.reasonMessage, v => str(v, 1000)), outputs: opt(r.outputs, stringMap) ?? {},
    failureCode: opt(r.failureCode, code), releaseId: id(r.releaseId), environment: code(r.environment), flowId: id(r.flowId), flowCode: str(r.flowCode, 128),
    flowVersionId: id(r.flowVersionId), flowVersionNo: int(r.flowVersionNo), evaluationTime: str(r.evaluationTime, 40), elapsedMillis: int(r.elapsedMillis),
    baselineEvaluationId: opt(r.baselineEvaluationId, id), shadowRunId: opt(r.shadowRunId, id), pathAvailable: bool(r.pathAvailable),
  };
}
function decodePathStep(v: unknown): PathStep {
  const r = record(v);
  return {
    stepNo: int(r.stepNo), nodeKey: str(r.nodeKey, 64), nodeType: code(r.nodeType), nodeName: opt(r.nodeName, v => str(v, 255)),
    ruleSetVersionId: opt(r.ruleSetVersionId, id), ruleSetCode: opt(r.ruleSetCode, v => str(v, 128)), ruleSetVersionNo: opt(r.ruleSetVersionNo, int),
    matched: opt(r.matched, bool), decision: opt(r.decision, code), reused: opt(r.reused, bool), exitEdgeKey: opt(r.exitEdgeKey, v => str(v, 64)), exitEdgeName: opt(r.exitEdgeName, v => str(v, 255)),
  };
}
function decodeOperand(v: unknown): ConditionOperand {
  const r = record(v);
  const literal = r.literalValue === null || r.literalValue === undefined ? null : typeof r.literalValue === 'boolean' ? String(r.literalValue) : str(r.literalValue, 1000);
  return {
    operandRole: code(r.operandRole), sourceType: code(r.sourceType), valueType: opt(r.valueType, code), variableCode: opt(r.variableCode, v => str(v, 128)),
    variableName: opt(r.variableName, v => str(v, 255)), literalValue: literal, numericUnit: opt(r.numericUnit, code), currencyCode: opt(r.currencyCode, v => str(v, 8)),
    currencyVariableCode: opt(r.currencyVariableCode, v => str(v, 128)),
  };
}
function decodeCondition(v: unknown, depth = 1): ConditionNode {
  invariant(depth <= MAX_CONDITION_DEPTH);
  const r = record(v);
  const nodeType = r.nodeType; invariant(nodeType === 'GROUP' || nodeType === 'PREDICATE');
  return {
    nodeKey: str(r.nodeKey, 64), nodeType, groupOperator: opt(r.groupOperator, code), comparisonOperator: opt(r.comparisonOperator, code),
    operands: opt(r.operands, v => list(v, decodeOperand, 64)) ?? [], result: opt(r.result, bool),
    children: opt(r.children, v => list(v, child => decodeCondition(child, depth + 1), 200)) ?? [],
  };
}
function decodeRule(v: unknown): RuleItem {
  const r = record(v);
  const conditionAvailable = r.conditionAvailable === true;
  return {
    ruleSetVersionId: id(r.ruleSetVersionId), ruleId: id(r.ruleId), ruleCode: str(r.ruleCode, 128), ruleName: str(r.ruleName, 255), priorityNo: int(r.priorityNo),
    matched: bool(r.matched), terminal: bool(r.terminal), action: opt(r.action, code), conditionAvailable,
    condition: conditionAvailable ? opt(r.condition, v => decodeCondition(v)) : null,
  };
}
function decodeVariable(v: unknown): VariableItem {
  const r = record(v);
  return {
    variableCode: str(r.variableCode, 128), variableName: opt(r.variableName, v => str(v, 255)), sourceType: code(r.sourceType), valueType: code(r.valueType),
    value: opt(r.value, v => { invariant(typeof v === 'string'); return v; }), values: opt(r.values, v => list(v, s => { invariant(typeof s === 'string'); return s; }, 10000)),
    valueBasis: code(r.valueBasis), technicalStatus: opt(r.technicalStatus, code), failureCode: opt(r.failureCode, code), elapsedMillis: opt(r.elapsedMillis, int),
  };
}
function decodeShadow(v: unknown): ShadowItem {
  const r = record(v);
  return { evaluationId: id(r.evaluationId), shadowRunId: id(r.shadowRunId), result: code(r.result), decisionChanged: bool(r.decisionChanged), outputChanged: bool(r.outputChanged), severity: opt(r.severity, code) };
}
export function decodeExplanation(v: unknown): EvaluationExplanation {
  const r = record(v);
  const inputs: Record<string, string | null> = {};
  for (const [k, value] of Object.entries(opt(r.inputs, record) ?? {})) { invariant(value === null || typeof value === 'string'); inputs[k] = value; }
  return {
    evaluationId: id(r.evaluationId), inputs, pathAvailable: bool(r.pathAvailable), path: opt(r.path, v => list(v, decodePathStep)) ?? [],
    rules: opt(r.rules, v => list(v, decodeRule)) ?? [], variables: opt(r.variables, v => list(v, decodeVariable)) ?? [], shadows: opt(r.shadows, v => list(v, decodeShadow, 100)) ?? [],
  };
}

/* ---------- 查询条件 ---------- */
export interface EvaluationQuery { type: EvaluationQueryType; id: string }
export const queryTypes: EvaluationQueryType[] = ['attempt', 'order', 'evaluation'];
export function queryTypeLabel(type: EvaluationQueryType): string { return t(`evaluation.queryType.${type}`); }
export function idFormatMessage(): string { return t('evaluation.idFormat'); }

/** 地址栏查询条件；未知类型按支付尝试处理，ID 原样保留（由 normalizeId 判断格式）。 */
export function parseEvaluationQuery(search: string): EvaluationQuery {
  const q = new URLSearchParams(search);
  const type = q.get('type');
  return { type: type === 'evaluation' || type === 'order' ? type : 'attempt', id: (q.get('id') ?? '').trim() };
}
export function evaluationQuerySearch(query: EvaluationQuery): string {
  return `?${new URLSearchParams({ type: query.type, id: query.id })}`;
}
/** 1～19 位数字、正数且不超过 Long 上限；返回去掉前导零的 ID，格式不符返回 null（不发请求）。 */
export function normalizeId(value: string): string | null {
  const s = value.trim();
  if (!/^\d{1,19}$/.test(s)) return null;
  const n = BigInt(s);
  return n > 0n && n <= MAX_ID ? n.toString() : null;
}

/* ---------- 结论 ---------- */
const resultColors: Record<EvaluationResult, string> = { ALLOW: 'green', REVIEW: 'orange', DENY: 'red', FAILED: 'default', TIMED_OUT: 'default' };
/** 结论、失败码与评估方式的显示名是 `risk.enum.evaluationResult|evaluationFailure|evaluationMode.<值>`。 */
const authentication = () => paymentOutputFallback()[0]!;

export function resultLabel(result: string): string { return enumLabel(t, 'enum.evaluationResult', result); }
/** 放行绿、审核橙、拒绝红、失败/超时灰；未知结论灰。 */
export function resultColor(result: string): string { return resultColors[result as EvaluationResult] ?? 'default'; }
export function authenticationLabel(outputs: Record<string, string>): string | null {
  const declaration = authentication();
  const value = outputs[declaration.outputCode];
  if (!value) return null;
  return declaration.allowedValues.find(v => v.value === value)?.name ?? value;
}
/** 结论标题：放行时附带认证要求，如“放行（需要 3DS）”。 */
export function conclusionTitle(evaluation: Pick<Evaluation, 'result' | 'outputs'>): string {
  const label = resultLabel(evaluation.result);
  return evaluation.result === 'ALLOW' && evaluation.outputs[authentication().outputCode] === 'THREE_DS_REQUIRED' ? t('evaluation.allowWithThreeDs', { label }) : label;
}
export function failureLabel(failureCode: string | null): string {
  if (!failureCode) return '—';
  const key = 'enum.evaluationFailure.' + failureCode;
  return t.has(key) ? t('evaluation.failureWithLabel', { code: failureCode, label: t(key) }) : failureCode;
}
export function modeLabel(mode: string): string { return enumLabel(t, 'enum.evaluationMode', mode); }
export function isShadow(evaluation: Pick<Evaluation, 'mode'>): boolean { return evaluation.mode === 'SHADOW'; }
/** UTC ISO 时间按“yyyy-MM-dd HH:mm:ss UTC”显示，与 Risk 存储口径一致。 */
export function formatUtc(value: string): string {
  const t = Date.parse(value);
  return Number.isFinite(t) ? `${new Date(t).toISOString().slice(0, 19).replace('T', ' ')} UTC` : value;
}

/* ---------- 路径 ---------- */
export function pathUnavailableText(): string { return t('evaluation.pathUnavailable'); }
export function stepName(step: PathStep): string { return step.nodeName ?? nodeTypeLabel(step.nodeType); }
export function ruleSetLabel(code: string | null, versionNo: number | null, fallbackId: string | null): string {
  if (code) return versionNo === null ? code : `${code} v${versionNo}`;
  return fallbackId ?? '—';
}
/** 路径步骤的摘要：规则集节点写命中与决定，结束节点写结论。 */
export function stepSummary(step: PathStep): string {
  if (step.nodeType === 'RULE_SET') {
    const parts = [t('evaluation.step.ruleSet', { ruleSet: ruleSetLabel(step.ruleSetCode, step.ruleSetVersionNo, step.ruleSetVersionId) }), matchedText(step.matched === true)];
    if (step.decision) parts.push(resultLabel(step.decision));
    return parts.join(' · ');
  }
  if (step.nodeType === 'END') return step.decision ? t('evaluation.step.endWithDecision', { decision: resultLabel(step.decision) }) : nodeTypeLabel('END');
  return '';
}
export function exitEdgeLabel(step: PathStep): string | null { return step.exitEdgeName ?? step.exitEdgeKey; }

/* ---------- 规则与条件 ---------- */
export interface RuleGroup { ruleSetVersionId: string; title: string; rules: RuleItem[] }
/** 按规则集版本分组（保持执行顺序）；标题取路径中的规则集编码与版本号，缺失时显示版本 ID。 */
export function groupRules(rules: readonly RuleItem[], path: readonly PathStep[]): RuleGroup[] {
  const groups: RuleGroup[] = [];
  for (const rule of rules) {
    let group = groups.find(g => g.ruleSetVersionId === rule.ruleSetVersionId);
    if (!group) {
      const step = path.find(s => s.ruleSetVersionId === rule.ruleSetVersionId);
      group = { ruleSetVersionId: rule.ruleSetVersionId, title: ruleSetLabel(step?.ruleSetCode ?? null, step?.ruleSetVersionNo ?? null, rule.ruleSetVersionId), rules: [] };
      groups.push(group);
    }
    group.rules.push(rule);
  }
  return groups;
}

/** 分组运算符的显示名是 `risk.enum.conditionGroup.<值>`。 */
export function predicateMark(result: PredicateResult): { mark: string; label: string; tone: 'pass' | 'fail' | 'none' } {
  if (result === true) return { mark: '✓', label: t('evaluation.predicate.true'), tone: 'pass' };
  if (result === false) return { mark: '✗', label: t('evaluation.predicate.false'), tone: 'fail' };
  return { mark: '—', label: t('evaluation.predicate.null'), tone: 'none' };
}
/** 规则与路径的命中文案。 */
function matchedText(matched: boolean): string { return matched ? t('evaluation.matched') : t('evaluation.notMatched'); }
/** 并列取值的分隔符（中文“、”，英文“, ”）。 */
function joinValues(values: readonly string[]): string { return values.join(t('evaluation.valueSeparator')); }

type NameOf = (variableCode: string) => string | null;
/** 数值字面量去掉存储精度带来的尾零（5000.00000000 → 5000、0.50 → 0.5）；非十进制文本原样返回。 */
export function plainDecimal(value: string): string {
  if (!/^-?\d+\.\d+$/.test(value)) return value;
  return value.replace(/0+$/, '').replace(/\.$/, '');
}
function literalText(operand: ConditionOperand, nameOf: NameOf): string {
  const raw = operand.literalValue ?? t('evaluation.literal.empty');
  const value = operand.valueType === 'BOOLEAN' ? (raw === 'true' ? t('evaluation.literal.true') : raw === 'false' ? t('evaluation.literal.false') : raw)
    : operand.valueType === 'NUMBER' ? plainDecimal(raw) : raw;
  if (operand.numericUnit !== 'MONEY') return value;
  const currency = operand.currencyCode ?? (operand.currencyVariableCode ? nameOf(operand.currencyVariableCode) ?? operand.currencyVariableCode : null);
  return currency ? t('evaluation.moneyLiteral', { value, currency }) : value;
}
export function operandText(operand: ConditionOperand, nameOf: NameOf = () => null): string {
  if (operand.sourceType === 'VARIABLE') return operand.variableName ?? (operand.variableCode ? nameOf(operand.variableCode) ?? operand.variableCode : '—');
  return literalText(operand, nameOf);
}
export function operatorLabel(operator: string | null): string { return operator ? variableOperatorLabel(operator) : ''; }
/** 谓词的可读表达：左值 运算符 右值（IN 列出全部值，BETWEEN 写“a 至 b”，为空类运算符没有右值）。 */
export function predicateText(node: ConditionNode, nameOf: NameOf = () => null): string {
  const side = (role: string) => node.operands.filter(o => o.operandRole === role).map(o => operandText(o, nameOf));
  const left = joinValues(side('LEFT')), right = side('RIGHT');
  const op = operatorLabel(node.comparisonOperator);
  if (!right.length) return `${left} ${op}`.trim();
  const rightText = node.comparisonOperator === 'BETWEEN' && right.length === 2 ? t('evaluation.between', { from: right[0]!, to: right[1]! }) : joinValues(right);
  return `${left} ${op} ${rightText}`;
}
export interface ConditionLine { key: string; depth: number; kind: 'GROUP' | 'PREDICATE'; text: string; result: PredicateResult }
/** 条件树展开为缩进行：分组写“全部满足/任一满足/不满足”，谓词写完整表达；每行带求值结果。 */
export function conditionLines(node: ConditionNode, nameOf: NameOf = () => null, depth = 0): ConditionLine[] {
  if (node.nodeType === 'PREDICATE') return [{ key: node.nodeKey, depth, kind: 'PREDICATE', text: predicateText(node, nameOf), result: node.result }];
  const head: ConditionLine = { key: node.nodeKey, depth, kind: 'GROUP', text: node.groupOperator ? enumLabel(t, 'enum.conditionGroup', node.groupOperator) : t('evaluation.group'), result: node.result };
  return [head, ...node.children.flatMap(child => conditionLines(child, nameOf, depth + 1))];
}
/** 条件中引用变量的中文名：先取解释变量表。 */
export function variableNameLookup(variables: readonly VariableItem[]): NameOf {
  const names = new Map(variables.filter(v => v.variableName).map(v => [v.variableCode, v.variableName!]));
  return code => names.get(code) ?? null;
}
export function ruleOutcome(rule: RuleItem): string {
  const parts = [matchedText(rule.matched)];
  if (rule.matched && rule.terminal) parts.push(t('evaluation.terminal'));
  if (rule.action) parts.push(rule.action);
  return parts.join(' · ');
}

/* ---------- 变量 ---------- */
export function currentValueNote(): string { return t('evaluation.currentValueNote'); }
export function isCurrentValue(variable: Pick<VariableItem, 'valueBasis'>): boolean { return variable.valueBasis === 'CURRENT'; }
export function variableFailed(variable: Pick<VariableItem, 'technicalStatus'>): boolean { return variable.technicalStatus === 'FAILED'; }
export function variableValueText(variable: VariableItem): string {
  if (variableFailed(variable)) return '—';
  if (variable.value !== null) return variable.value;
  if (variable.values) return variable.values.length ? joinValues(variable.values) : t('evaluation.emptySet');
  return '—';
}
/** 依据列：评估时（含 Feature 耗时）/ 当前值 / 失败 + 失败码。 */
export function variableBasisText(variable: VariableItem): string {
  if (variableFailed(variable)) return variable.failureCode ? t('evaluation.basis.failedWithCode', { code: variable.failureCode }) : t('evaluation.basis.failed');
  if (isCurrentValue(variable)) return t('evaluation.basis.current');
  if (variable.valueBasis === 'EVALUATION') return variable.elapsedMillis === null ? t('evaluation.basis.evaluation') : t('evaluation.basis.evaluationElapsed', { millis: variable.elapsedMillis });
  return variable.valueBasis;
}

/* ---------- 输入 ---------- */
export type InputField = { key: string; label: string };
/** 支付尝试输入快照的 17 个字段（顺序同 Risk 输入合同）；显示名是 `risk.evaluation.input.<字段>`。 */
export const PAYMENT_INPUT_FIELDS: readonly string[] = [
  'paymentIntentId', 'riskActionId', 'orderId', 'clientId', 'entityNo', 'paymentMethodCode', 'captureMode', 'amount', 'currency',
  'industryCode', 'mccCode', 'consumerCountryCode', 'consumerReference', 'clientIpAddress', 'userAgent', 'cardFingerprint', 'cardBin',
];
/** 结账准备输入快照的 12 个字段（顺序同 Risk 结账输入合同 v5）；订单 ID 是业务身份，显示在结论卡片中。 */
export const CHECKOUT_INPUT_FIELDS: readonly string[] = [
  'clientId', 'entityNo', 'entitySourceType', 'localPartnerId', 'consumerCountryCode', 'currency',
  'merchandiseAmount', 'discountAmount', 'netAmount', 'industryCode', 'mccCode', 'lineCount',
];
const POINT_INPUT_FIELDS: Record<string, readonly string[]> = { PAYMENT_ATTEMPT_RISK: PAYMENT_INPUT_FIELDS, CHECKOUT_PREPARE: CHECKOUT_INPUT_FIELDS };
/** 风控点的已知输入字段；未知风控点没有已知字段，全部按原键显示。 */
export function inputFieldsOf(pointCode: string): readonly InputField[] {
  return (POINT_INPUT_FIELDS[pointCode] ?? []).map(key => ({ key, label: t(`evaluation.input.${key}`) }));
}
/** 结论卡片中业务身份的标签；未知风控点显示“业务身份”。 */
export function businessKeyLabel(pointCode: string): string { return tOr(t, 'evaluation.businessKey.' + pointCode, t('evaluation.businessKey.default')); }
export function notProvidedText(): string { return t('evaluation.notProvided'); }
/** 该点已知字段在前，Risk 新增的未知字段按原键追加在后；空值为 null（页面显示“未提供”）。 */
export function inputRows(inputs: Record<string, string | null>, pointCode: string): { key: string; label: string; value: string | null }[] {
  const fields = inputFieldsOf(pointCode);
  const known = fields.map(f => ({ ...f, value: inputs[f.key] || null }));
  const extra = Object.keys(inputs).filter(k => !fields.some(f => f.key === k)).map(k => ({ key: k, label: k, value: inputs[k] || null }));
  return [...known, ...extra];
}

/* ---------- 影子评估 ---------- */
export function changedLabel(changed: boolean): string { return changed ? t('evaluation.changed.yes') : t('evaluation.changed.no'); }
