import { decodeVariableOperand, decodeVariableSummary, decodeVariableIssue, decodeVariableQueryContext, prepareOperands } from './riskVariable.ts';
import type { PagingResponseBody } from '../api/types.ts';
import type {
  DeleteRuleSetResponse, RuleSetVersionSummary, AdditionalAction, ActionParameter, ActionParameterDescriptor, TargetBindingDescriptor,
  CreateRuleSetVersionResponse, CreateTestCandidateResponse, DecisionActionDescriptor,
  DecisionInputFieldDescriptor, DecisionPointDescriptor, FreezeRuleSetVersionResponse, FrozenComposition,
  InitialStateChange, ReleaseStateChange, ReleaseStaticCheck, RuleAction, RuleAuthoringContext,
  RuleConditionNode, RuleDefinition, RuleOperand, RuleSetDetail, RuleSetListItem, TestCandidateDetail,
  TestCandidateListItem, ValidateTestCandidateResponse, ValidationCheck,
} from './riskApiModels.ts';

type Decoder<T> = (value: unknown) => T;
const codes = /^[A-Z][A-Z0-9_]{0,63}$/;
const decimalId = /^[1-9]\d*$/;
const hash = /^[0-9a-f]{64}$/;
const trigger = /^[0-9a-f]{64}$/;
const utcMicros = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/;
const nodeKey = /^[\x21-\x7e]{1,64}$/;
const operatorCodes = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL'] as const;
const valueTypes = ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION'] as const;
const candidateStatuses = ['DRAFT', 'VALIDATING', 'REJECTED'] as const;
const checkOrder = ['REFERENCE_INTEGRITY', 'AST_SHAPE', 'AST_TYPES', 'FEATURE_DEPENDENCY', 'HANDLER_HASH', 'ACTION_PARAMETERS', 'TERMINAL_ACTION', 'SYSTEM_OBLIGATION'];

function fail(): never { throw new Error('invalid Risk response'); }
function exactRecord(value: unknown, keys: readonly string[]): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return fail();
  const actual = Object.keys(value).sort(); const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) return fail();
  return value as Record<string, unknown>;
}
function stringValue(value: unknown, pattern = /[\s\S]*/, maximum = 4096): string {
  if (typeof value !== 'string' || value.length > maximum || !pattern.test(value)) return fail();
  return value;
}
function nullableString(value: unknown, pattern: RegExp, maximum: number): string | null {
  return value === null ? null : stringValue(value, pattern, maximum);
}
function arrayValue<T>(value: unknown, decode: Decoder<T>): T[] {
  if (!Array.isArray(value)) return fail();
  return value.map(decode);
}
function enumValue<T extends readonly string[]>(value: unknown, values: T): T[number] {
  return typeof value === 'string' && (values as readonly string[]).includes(value) ? value as T[number] : fail();
}
function positive(value: unknown): number { return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : fail(); }
function nonnegative(value: unknown): number { return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : fail(); }
function booleanValue(value: unknown): boolean { return typeof value === 'boolean' ? value : fail(); }
function text(value: unknown, maximum = 255): string { const result = stringValue(value, /[\s\S]*/, maximum); return result.trim() === result && result.normalize('NFC') === result && result.length > 0 ? result : fail(); }
function timestamp(value: unknown): string { const result = stringValue(value, utcMicros, 27); const year = Number(result.slice(0, 4)); return year >= 1000 && year <= 9999 && Number.isFinite(Date.parse(result)) && new Date(Date.parse(result)).toISOString().slice(0, 19) === result.slice(0, 19) ? result : fail(); }
function orderedUnique<T>(items: readonly T[], identity: (item: T) => string, compare?: (left: T, right: T) => number) {
  const seen = new Set<string>();
  for (let index = 0; index < items.length; index++) {
    const id = identity(items[index]!); if (seen.has(id) || (index > 0 && compare && compare(items[index - 1]!, items[index]!) >= 0)) fail(); seen.add(id);
  }
}

function decodePoint(value: unknown): DecisionPointDescriptor {
  const source = exactRecord(value, ['pointCode', 'pointName', 'businessDomain', 'inputContractCode', 'inputContractVersion', 'outputContractCode', 'defaultDecision', 'evaluationStrategy', 'failureStrategy', 'timeoutMillis', 'status']);
  return { pointCode: stringValue(source.pointCode, codes, 64), pointName: text(source.pointName), businessDomain: stringValue(source.businessDomain, codes, 64), inputContractCode: stringValue(source.inputContractCode, codes, 64), inputContractVersion: positive(source.inputContractVersion), outputContractCode: stringValue(source.outputContractCode, codes, 64), defaultDecision: stringValue(source.defaultDecision, codes, 64), evaluationStrategy: stringValue(source.evaluationStrategy, codes, 64), failureStrategy: stringValue(source.failureStrategy, codes, 64), timeoutMillis: positive(source.timeoutMillis), status: stringValue(source.status, codes, 64) };
}
function decodeField(value: unknown): DecisionInputFieldDescriptor {
  const modern=!!value&&typeof value==='object'&&'variableCode' in value;
  const source = exactRecord(value, ['fieldCode', 'valueType', 'requiredFlag', 'dataClassification', 'ruleReferenceFlag', 'actionParameterFlag',...(modern?['variableCode']:[])]);
  return { fieldCode: stringValue(source.fieldCode, /^[a-z][a-z0-9_]{0,63}$/, 64), valueType: enumValue(source.valueType, valueTypes), requiredFlag: booleanValue(source.requiredFlag), dataClassification: stringValue(source.dataClassification, codes, 64), ruleReferenceFlag: booleanValue(source.ruleReferenceFlag), actionParameterFlag: booleanValue(source.actionParameterFlag),...(modern?{variableCode:nullableString(source.variableCode,codes,64)}:{}) };
}
function decodeActionDescriptor(value: unknown): DecisionActionDescriptor {
  const source = exactRecord(value, ['actionCode', 'actionName', 'actionCategory', 'versionNo', 'terminalFlag', 'decisionCode', 'mergePolicy', 'shadowSupportedFlag', 'status']);
  if (source.terminalFlag === true ? source.decisionCode === null : source.decisionCode !== null) fail();
  return { actionCode: stringValue(source.actionCode, codes, 64), actionName: text(source.actionName), actionCategory: stringValue(source.actionCategory, codes, 64), versionNo: positive(source.versionNo), terminalFlag: booleanValue(source.terminalFlag), decisionCode: nullableString(source.decisionCode, codes, 64), mergePolicy: stringValue(source.mergePolicy, codes, 64), shadowSupportedFlag: booleanValue(source.shadowSupportedFlag), status: stringValue(source.status, codes, 64) };
}
export function decodeRuleAuthoringContext(value: unknown): RuleAuthoringContext {
  const source = exactRecord(value, ['descriptorCode', 'point', 'fields', 'operators', 'actions', 'features', 'actionParameters', 'obligations', 'limitBindings', 'targetBindings',...((value as RuleAuthoringContext)?.operandFormat?['operandFormat','variableQueryContext']:[])]);
  const fields = arrayValue(source.fields, decodeField); const operators = arrayValue(source.operators, value => enumValue(value, operatorCodes)); const actions = arrayValue(source.actions, decodeActionDescriptor);
  if (!Array.isArray(source.features) || source.features.length || !Array.isArray(source.obligations) || source.obligations.length || !Array.isArray(source.limitBindings) || source.limitBindings.length) fail();
  orderedUnique(fields, field => field.variableCode??field.fieldCode, source.operandFormat ? undefined : (a,b)=>a.fieldCode.localeCompare(b.fieldCode));
  orderedUnique(operators, operator => operator);
  orderedUnique(actions, action => action.actionCode, (left, right) => left.actionCode.localeCompare(right.actionCode) || left.versionNo - right.versionNo);
  if (operators.length !== operatorCodes.length || operators.some((operator, index) => operator !== operatorCodes[index])) fail();
  return { descriptorCode: stringValue(source.descriptorCode, codes, 128), point: decodePoint(source.point), fields, operators, actions, features: [], actionParameters: arrayValue(source.actionParameters, decodeParameterDescriptor), targetBindings: arrayValue(source.targetBindings, decodeTarget), obligations: [], limitBindings: [],...(source.operandFormat?{operandFormat:enumValue(source.operandFormat,['VARIABLE_REFERENCE']),variableQueryContext:decodeVariableQueryContext(source.variableQueryContext)}:{}) };
}
function decodeOperand(value: unknown): RuleOperand {
  if(value&&typeof value==='object'&&'variableCode' in value) {
    if((value as RuleOperand).sourceType!=='CONTEXT')return decodeVariableOperand(value);
    const r=exactRecord(value,['operandRole','valueSequence','sourceType','fieldCode','valueType','literalValue','variableCode','numericUnit','currencyCode','currencyVariableCode']);
    if(['variableCode','numericUnit','currencyCode','currencyVariableCode'].some(k=>r[k]!==null))fail();
    return decodeOperand(Object.fromEntries(Object.entries(r).filter(([k])=>!['variableCode','numericUnit','currencyCode','currencyVariableCode'].includes(k))));
  }
  const source = exactRecord(value, ['operandRole', 'valueSequence', 'sourceType', 'fieldCode', 'valueType', 'literalValue']);
  const sourceType = enumValue(source.sourceType, ['CONTEXT', 'LITERAL'] as const); const fieldCode = nullableString(source.fieldCode, /^[a-z][a-z0-9_]{0,63}$/, 64); const literalValue = source.literalValue;
  if (!(literalValue === null || typeof literalValue === 'string' || typeof literalValue === 'boolean')) fail();
  if (sourceType === 'CONTEXT' ? fieldCode === null || literalValue !== null : fieldCode !== null) fail();
  return { operandRole: enumValue(source.operandRole, ['LEFT', 'RIGHT'] as const), valueSequence: positive(source.valueSequence), sourceType, fieldCode, valueType: enumValue(source.valueType, valueTypes), literalValue };
}
function decodeNode(value: unknown): RuleConditionNode {
  const source = exactRecord(value, ['nodeKey', 'parentNodeKey', 'sequenceNo', 'nodeType', 'groupOperator', 'comparisonOperator', 'operands']);
  return { nodeKey: stringValue(source.nodeKey, nodeKey, 64), parentNodeKey: nullableString(source.parentNodeKey, nodeKey, 64), sequenceNo: positive(source.sequenceNo), nodeType: enumValue(source.nodeType, ['GROUP', 'PREDICATE'] as const), groupOperator: source.groupOperator === null ? null : enumValue(source.groupOperator, ['AND', 'OR', 'NOT'] as const), comparisonOperator: source.comparisonOperator === null ? null : enumValue(source.comparisonOperator, operatorCodes), operands: arrayValue(source.operands, decodeOperand) };
}
function decodeTerminal(value: unknown): RuleAction {
  const source = exactRecord(value, ['actionCode', 'actionVersionNo']);
  return { actionCode: stringValue(source.actionCode, codes, 64), actionVersionNo: positive(source.actionVersionNo) };
}
function decodeTarget(value: unknown): TargetBindingDescriptor {
  const modern = !!value && typeof value === 'object' && 'subjectSemanticRole' in value;
  const source = exactRecord(value, ['bindingCode', 'displayName', 'subjectType', 'requiredFields',...(modern?['subjectSemanticRole']:[])]);
  const requiredFields = arrayValue(source.requiredFields, value => {
    const field = exactRecord(value, ['role', 'fieldCode']);
    return { role: stringValue(field.role, codes, 64), fieldCode: stringValue(field.fieldCode, /^[a-z][a-z0-9_]{0,63}$/, 64) };
  });
  if (source.subjectSemanticRole ? requiredFields.length !== 0 : !requiredFields.length) fail();
  orderedUnique(requiredFields, field => field.role);
  return { bindingCode: stringValue(source.bindingCode, codes, 64), displayName: text(source.displayName), subjectType: stringValue(source.subjectType, codes, 64), requiredFields,...(modern?{subjectSemanticRole:nullableString(source.subjectSemanticRole,codes,64)}:{}) };
}
function decodeParameterDescriptor(value: unknown): ActionParameterDescriptor {
  const source = exactRecord(value, ['actionCode', 'actionVersionNo', 'parameterCode', 'valueType', 'requiredFlag', 'allowedSourceType', 'targetParameterFlag', 'maxValues', 'minimumValue', 'maximumValue', 'valuePattern']);
  return { actionCode: stringValue(source.actionCode, codes, 64), actionVersionNo: positive(source.actionVersionNo), parameterCode: stringValue(source.parameterCode, codes, 64), valueType: enumValue(source.valueType, valueTypes), requiredFlag: booleanValue(source.requiredFlag), allowedSourceType: enumValue(source.allowedSourceType, ['CONTEXT', 'VARIABLE', 'LITERAL'] as const), targetParameterFlag: booleanValue(source.targetParameterFlag), maxValues: positive(source.maxValues), minimumValue: nullableString(source.minimumValue, decimalId, 30), maximumValue: nullableString(source.maximumValue, decimalId, 30), valuePattern: nullableString(source.valuePattern, /[\s\S]*/, 512) };
}
function decodeAdditional(value: unknown): AdditionalAction {
  const source = exactRecord(value, ['sequenceNo', 'actionCode', 'actionVersionNo', 'targetBindingCode', 'parameters',...((value&&typeof value==='object'&&'subjectVariableCode' in value)?['subjectVariableCode']:[])]);
  const parameters = arrayValue(source.parameters, (value): ActionParameter => {
    const parameter = exactRecord(value, ['parameterCode', 'valueSequence', 'sourceType', 'fieldCode', 'valueType', 'literalValue']);
    const literalValue = parameter.literalValue;
    if (typeof literalValue !== 'string') fail();
    if (parameter.sourceType !== 'LITERAL' || parameter.fieldCode !== null) fail();
    return { parameterCode: stringValue(parameter.parameterCode, codes, 64), valueSequence: positive(parameter.valueSequence), sourceType: 'LITERAL', fieldCode: null, valueType: enumValue(parameter.valueType, valueTypes), literalValue };
  });
  if (!parameters.length || parameters.length > 32) fail();
  orderedUnique(parameters, parameter => `${parameter.parameterCode}:${parameter.valueSequence}`);
  for (const name of new Set(parameters.map(parameter => parameter.parameterCode))) {
    if (parameters.filter(parameter => parameter.parameterCode === name).some((parameter, index) => parameter.valueSequence !== index + 1)) fail();
  }
  return { sequenceNo: positive(source.sequenceNo), actionCode: stringValue(source.actionCode, codes, 64), actionVersionNo: positive(source.actionVersionNo), targetBindingCode: stringValue(source.targetBindingCode, codes, 64), parameters,...('subjectVariableCode' in source?{subjectVariableCode:nullableString(source.subjectVariableCode,codes,64)}:{}) };
}
function decodeRule(value: unknown): RuleDefinition {
  const source = exactRecord(value, ['ruleCode', 'ruleName', 'priorityNo', 'enabledFlag', 'reasonCode', 'conditionNodes', 'action', 'reasonMessage', 'additionalActions']);
  const action: RuleAction | null = source.action === null ? null : decodeTerminal(source.action);
  const additionalActions = arrayValue(source.additionalActions, decodeAdditional);
  if (additionalActions.length > 16 || additionalActions.some((action, index) => action.sequenceNo !== index + 1)) fail();
  return { ruleCode: stringValue(source.ruleCode, codes, 64), ruleName: text(source.ruleName), priorityNo: positive(source.priorityNo), enabledFlag: booleanValue(source.enabledFlag), reasonCode: nullableString(source.reasonCode, codes, 64), conditionNodes: arrayValue(source.conditionNodes, decodeNode), action, reasonMessage: source.reasonMessage === null ? null : text(source.reasonMessage, 500), additionalActions };
}
function literalMatchesValueType(value: string | boolean | null, valueType: RuleOperand['valueType']): boolean {
  if (value === null) return false;
  if (valueType === 'BOOLEAN') return typeof value === 'boolean';
  if (valueType === 'STRING') return typeof value === 'string' && value.length <= 512;
  if (valueType === 'NUMBER') return typeof value === 'string' && /^-?(?:0|[1-9]\d{0,15})(?:\.\d{1,8})?$/.test(value) && !/^-0(?:\.0+)?$/.test(value);
  if (valueType === 'DATETIME') return typeof value === 'string' && timestamp(value) === value;
  return typeof value === 'string' && /^(?:0|[1-9]\d*)$/.test(value);
}
function validatePredicateOperands(node: RuleConditionNode, variableFormat=false) {
  if(variableFormat){prepareOperands(node.comparisonOperator!,node.operands);return;}
  const context = node.operands.filter(operand => operand.sourceType === 'CONTEXT');
  const literals = node.operands.filter(operand => operand.sourceType === 'LITERAL');
  if (context.length !== 1 || context[0]!.valueSequence !== 1 || node.operands.some(operand => operand.valueType !== context[0]!.valueType)) fail();
  const operator = node.comparisonOperator!;
  if (['GT', 'GTE', 'LT', 'LTE', 'BETWEEN'].includes(operator) && !['NUMBER', 'DATETIME', 'DURATION'].includes(context[0]!.valueType)) fail();
  const nullary = operator === 'IS_NULL' || operator === 'IS_NOT_NULL';
  const requiredCount = operator === 'BETWEEN' ? 2 : operator === 'IN' || operator === 'NOT_IN' ? -1 : 1;
  if (nullary ? literals.length !== 0 : literals.length !== requiredCount && !(requiredCount === -1 && literals.length >= 1 && literals.length <= 100)) fail();
  const literalRole = context[0]!.operandRole === 'LEFT' ? 'RIGHT' : 'LEFT';
  if (literals.some((literal, index) => literal.valueSequence !== index + 1 || literal.operandRole !== literalRole || !literalMatchesValueType(literal.literalValue, literal.valueType))) fail();
}
function validateRuleSetDetailSemantics(detail: RuleSetDetail) {
  if (detail.rules.length === 0 || detail.rules.length > 200) fail();
  let nodeCount = 0; let operandCount = 0;
  orderedUnique(detail.rules, rule => rule.ruleCode); orderedUnique(detail.rules, rule => String(rule.priorityNo));
  for (const rule of detail.rules) {
    nodeCount += rule.conditionNodes.length;
    operandCount += rule.conditionNodes.reduce((count, node) => count + node.operands.length, 0);
    if (rule.conditionNodes.length === 0 || nodeCount > 2_000 || operandCount > 10_000) fail();
    const byKey = new Map<string, RuleConditionNode>(); for (const node of rule.conditionNodes) { if (byKey.has(node.nodeKey)) fail(); byKey.set(node.nodeKey, node); }
    const roots = rule.conditionNodes.filter(node => node.parentNodeKey === null); if (roots.length !== 1 || roots[0]!.sequenceNo !== 1) fail();
    const children = new Map<string, RuleConditionNode[]>();
    for (const node of rule.conditionNodes) if (node.parentNodeKey !== null) { if (!byKey.has(node.parentNodeKey)) fail(); const list = children.get(node.parentNodeKey) ?? []; list.push(node); children.set(node.parentNodeKey, list); }
    for (const list of children.values()) { list.sort((left, right) => left.sequenceNo - right.sequenceNo); if (list.some((item, index) => item.sequenceNo !== index + 1)) fail(); }
    let visited = 0; const visit = (node: RuleConditionNode, depth: number) => { if (++visited > rule.conditionNodes.length || depth > 12) fail(); const nested = children.get(node.nodeKey) ?? [];
      if (node.nodeType === 'GROUP') { if (node.groupOperator === null || node.comparisonOperator !== null || node.operands.length !== 0 || (node.groupOperator === 'NOT' ? nested.length !== 1 : nested.length < 2)) fail(); nested.forEach(child => visit(child, depth + 1)); return; }
      if (node.groupOperator !== null || node.comparisonOperator === null || nested.length !== 0) fail();
      validatePredicateOperands(node,detail.operandFormat==='VARIABLE_REFERENCE');
    }; visit(roots[0]!, 1); if (visited !== rule.conditionNodes.length) fail();
  }
}
function decodeSummary(value: unknown): RuleSetVersionSummary {
  const source = exactRecord(value, ['ruleSetVersionId', 'pointVersionId', 'pointCode', 'ruleSetCode', 'ruleSetName', 'versionNo', 'status', 'contentHash', 'createdBy', 'changeReason', 'createTime', 'frozenBy', 'frozenTime']);
  const frozenBy = nullableString(source.frozenBy, decimalId, 30); const frozenTime = source.frozenTime === null ? null : timestamp(source.frozenTime); if ((frozenBy === null) !== (frozenTime === null)) fail();
  return { ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), pointVersionId: nullableString(source.pointVersionId, decimalId, 30), pointCode: stringValue(source.pointCode, codes, 64), ruleSetCode: stringValue(source.ruleSetCode, codes, 64), ruleSetName: text(source.ruleSetName), versionNo: positive(source.versionNo), status: enumValue(source.status, ['DRAFT', 'FROZEN', 'RETIRED'] as const), contentHash: stringValue(source.contentHash, hash, 64), createdBy: stringValue(source.createdBy, decimalId, 30), changeReason: text(source.changeReason, 500), createTime: timestamp(source.createTime), frozenBy, frozenTime };
}
function decodePage<T>(value: unknown, decode: Decoder<T>): PagingResponseBody<T> {
  const source = exactRecord(value, ['items', 'page', 'size', 'total']); const items = arrayValue(source.items, decode); const page = positive(source.page); const size = positive(source.size); const total = nonnegative(source.total); const offset = (page - 1) * size;
  const expectedItems = Math.min(size, Math.max(total - offset, 0));
  if (!Number.isSafeInteger(offset) || items.length !== expectedItems) fail(); return { items, page, size, total };
}
function positiveLong(value: unknown): string {
  const result = stringValue(value, decimalId, 19);
  return BigInt(result) <= 9223372036854775807n ? result : fail();
}
function decodeRuleSetListItem(value: unknown): RuleSetListItem {
  const source = exactRecord(value, [...Object.keys(decodeSummaryKeys), 'ruleSetId', 'managementVersion', 'inUse']);
  const { ruleSetId, managementVersion, inUse, ...fields } = source;
  const summary = decodeSummary(fields);
  if (summary.status === 'RETIRED') fail();
  return { ...summary, ruleSetId: positiveLong(ruleSetId), managementVersion: positiveLong(managementVersion), inUse: booleanValue(inUse) };
}
export function decodeRuleSetPage(value: unknown): PagingResponseBody<RuleSetListItem> {
  const page = decodePage(value, decodeRuleSetListItem);
  orderedUnique(page.items, item => item.ruleSetVersionId);
  orderedUnique(page.items, item => item.ruleSetId);
  orderedUnique(page.items, item => `${item.pointCode}:${item.ruleSetCode}`);
  return page;
}
export function decodeDeleteRuleSetResponse(value: unknown): DeleteRuleSetResponse {
  const source = exactRecord(value, ['ruleSetId', 'status', 'version', 'deletedBy', 'deletedTime']);
  return { ruleSetId: positiveLong(source.ruleSetId), status: enumValue(source.status, ['DELETED'] as const),
    version: positiveLong(source.version), deletedBy: positiveLong(source.deletedBy), deletedTime: timestamp(source.deletedTime) };
}
export function decodeRuleSetDetail(value: unknown): RuleSetDetail {
  const modern=!!value&&typeof value==='object'&&'operandFormat' in value;
  const source=exactRecord(value,[...Object.keys(decodeSummaryKeys),'rules',...(modern?['operandFormat','currentVariables','valuesAsOf','variableIssues']:[])]);
  const summary=decodeSummary(Object.fromEntries(Object.entries(source).filter(([key])=>key in decodeSummaryKeys)));
  const result:RuleSetDetail={...summary,rules:arrayValue(source.rules,decodeRule),...(modern?{
    operandFormat:source.operandFormat===null?null:enumValue(source.operandFormat,['LEGACY','VARIABLE_REFERENCE']),
    currentVariables:arrayValue(source.currentVariables,decodeVariableSummary),valuesAsOf:source.valuesAsOf===null?null:timestamp(source.valuesAsOf),variableIssues:arrayValue(source.variableIssues,decodeVariableIssue)}:{})};
  validateRuleSetDetailSemantics(result);return result;
}

const decodeSummaryKeys = { ruleSetVersionId: true, pointVersionId: true, pointCode: true, ruleSetCode: true, ruleSetName: true, versionNo: true, status: true, contentHash: true, createdBy: true, changeReason: true, createTime: true, frozenBy: true, frozenTime: true };
export function decodeCreateRuleSetVersionResponse(value: unknown): CreateRuleSetVersionResponse { const source = exactRecord(value, ['ruleSetVersionId', 'pointVersionId', 'ruleSetCode', 'versionNo', 'status', 'contentHash', 'createdBy', 'createTime']); return { ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), pointVersionId: nullableString(source.pointVersionId, decimalId, 30), ruleSetCode: stringValue(source.ruleSetCode, codes, 64), versionNo: positive(source.versionNo), status: enumValue(source.status, ['DRAFT'] as const), contentHash: stringValue(source.contentHash, hash, 64), createdBy: stringValue(source.createdBy, decimalId, 30), createTime: timestamp(source.createTime) }; }
export function decodeFreezeRuleSetVersionResponse(value: unknown): FreezeRuleSetVersionResponse { const source = exactRecord(value, ['ruleSetVersionId', 'pointVersionId', 'ruleSetCode', 'versionNo', 'status', 'contentHash', 'frozenBy', 'frozenTime']); return { ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), pointVersionId: nullableString(source.pointVersionId, decimalId, 30), ruleSetCode: stringValue(source.ruleSetCode, codes, 64), versionNo: positive(source.versionNo), status: enumValue(source.status, ['FROZEN'] as const), contentHash: stringValue(source.contentHash, hash, 64), frozenBy: stringValue(source.frozenBy, decimalId, 30), frozenTime: timestamp(source.frozenTime) }; }
function decodeCandidateSummary(value: unknown): TestCandidateListItem { const source = exactRecord(value, ['releaseId', 'releaseNo', 'decisionPointId', 'pointCode', 'pointVersionId', 'ruleSetVersionId', 'environmentCode', 'status', 'version', 'contentHash', 'createdBy', 'createTime', 'allStaticChecksPassed']); const status = enumValue(source.status, candidateStatuses); const version = positive(source.version); const passed = source.allStaticChecksPassed === null ? null : booleanValue(source.allStaticChecksPassed); if ((status === 'DRAFT' && (version !== 1 || passed !== null)) || (status === 'VALIDATING' && (version !== 2 || (passed !== null && passed !== true))) || (status === 'REJECTED' && (version !== 3 || passed !== false))) fail(); return { releaseId: stringValue(source.releaseId, decimalId, 30), releaseNo: positive(source.releaseNo), decisionPointId: stringValue(source.decisionPointId, decimalId, 30), pointCode: stringValue(source.pointCode, codes, 64), pointVersionId: stringValue(source.pointVersionId, decimalId, 30), ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), environmentCode: enumValue(source.environmentCode, ['TEST'] as const), status, version, contentHash: stringValue(source.contentHash, hash, 64), createdBy: stringValue(source.createdBy, decimalId, 30), createTime: timestamp(source.createTime), allStaticChecksPassed: passed }; }
export function decodeCandidatePage(value: unknown): PagingResponseBody<TestCandidateListItem> { const page = decodePage(value, decodeCandidateSummary); orderedUnique(page.items, item => item.releaseId); return page; }
function decodeComposition(value: unknown): FrozenComposition { const source = exactRecord(value, ['decisionPointId', 'pointCode', 'pointVersionId', 'pointVersionNo', 'pointVersionContentHash', 'ruleSetVersionId', 'ruleSetCode', 'ruleSetVersionNo', 'ruleSetContentHash']); return { decisionPointId: stringValue(source.decisionPointId, decimalId, 30), pointCode: stringValue(source.pointCode, codes, 64), pointVersionId: stringValue(source.pointVersionId, decimalId, 30), pointVersionNo: positive(source.pointVersionNo), pointVersionContentHash: stringValue(source.pointVersionContentHash, hash, 64), ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), ruleSetCode: stringValue(source.ruleSetCode, codes, 64), ruleSetVersionNo: positive(source.ruleSetVersionNo), ruleSetContentHash: stringValue(source.ruleSetContentHash, hash, 64) }; }
function decodeState(value: unknown): ReleaseStateChange { const source = exactRecord(value, ['auditEventId', 'eventTime', 'fromStatus', 'toStatus', 'reasonCode', 'triggerType', 'triggerId', 'actorId', 'aggregateVersion']); return { auditEventId: stringValue(source.auditEventId, decimalId, 30), eventTime: timestamp(source.eventTime), fromStatus: source.fromStatus === null ? null : enumValue(source.fromStatus, candidateStatuses), toStatus: enumValue(source.toStatus, candidateStatuses), reasonCode: stringValue(source.reasonCode, codes, 64), triggerType: enumValue(source.triggerType, ['OPERATIONS_COMMAND'] as const), triggerId: stringValue(source.triggerId, trigger, 64), actorId: stringValue(source.actorId, decimalId, 30), aggregateVersion: positive(source.aggregateVersion) }; }
function decodeCheck(value: unknown): ReleaseStaticCheck { const source = exactRecord(value, ['auditEventId', 'eventTime', 'checkCode', 'checkStatus', 'severity', 'errorCode', 'errorSummary']); return { auditEventId: stringValue(source.auditEventId, decimalId, 30), eventTime: timestamp(source.eventTime), checkCode: stringValue(source.checkCode, codes, 64), checkStatus: enumValue(source.checkStatus, ['PASSED', 'FAILED'] as const), severity: enumValue(source.severity, ['INFO', 'ERROR'] as const), errorCode: nullableString(source.errorCode, codes, 64), errorSummary: source.errorSummary === null ? null : text(source.errorSummary, 500) }; }
function validateChecks(checks: readonly ReleaseStaticCheck[]) {
  orderedUnique(checks, check => check.auditEventId);
  if (checks.length !== 0 && (checks.length !== 8 || checks.some((check, index) => check.checkCode !== checkOrder[index]))) fail();
  for (const check of checks) {
    const passed = check.checkStatus === 'PASSED';
    if (passed ? check.severity !== 'INFO' || check.errorCode !== null || check.errorSummary !== null : check.severity !== 'ERROR' || check.errorCode === null || check.errorSummary === null) fail();
  }
}
function validateCandidateDetailSemantics(detail: TestCandidateDetail) {
  const states = detail.stateChanges;
  orderedUnique(states, state => state.auditEventId);
  if (states.length < 1 || states.length > 3 || states.some((state, index) => state.aggregateVersion !== index + 1 || (index > 0 && state.eventTime < states[index - 1]!.eventTime))) fail();
  const initial = states[0]!;
  if (initial.fromStatus !== null || initial.toStatus !== 'DRAFT' || initial.reasonCode !== 'CANDIDATE_CREATED' || initial.aggregateVersion !== 1 || initial.actorId !== detail.createdBy) fail();
  validateChecks(detail.staticChecks);
  if (detail.status === 'DRAFT') {
    if (detail.version !== 1 || states.length !== 1 || detail.staticChecks.length !== 0 || detail.allStaticChecksPassed !== null || detail.waitingForShadow) fail();
    return;
  }
  const validating = states[1];
  if (!validating || validating.fromStatus !== 'DRAFT' || validating.toStatus !== 'VALIDATING' || validating.reasonCode !== 'STATIC_VALIDATION_STARTED') fail();
  if (detail.status === 'VALIDATING') {
    const passed = detail.staticChecks.length === 8;
    if (detail.version !== 2 || states.length !== 2 || detail.allStaticChecksPassed !== (passed ? true : null) || detail.waitingForShadow !== passed || (passed && detail.staticChecks.some(check => check.checkStatus !== 'PASSED'))) fail();
    return;
  }
  const rejected = states[2];
  if (!rejected || detail.version !== 3 || states.length !== 3 || rejected.fromStatus !== 'VALIDATING' || rejected.toStatus !== 'REJECTED' || rejected.reasonCode !== 'STATIC_VALIDATION_FAILED' || rejected.actorId !== validating.actorId || rejected.triggerId !== validating.triggerId || detail.staticChecks.length !== 8 || detail.allStaticChecksPassed !== false || detail.waitingForShadow || !detail.staticChecks.some(check => check.checkStatus === 'FAILED')) fail();
}
export function decodeCandidateDetail(value: unknown): TestCandidateDetail { const source = exactRecord(value, ['releaseId', 'releaseNo', 'decisionPointId', 'pointCode', 'pointVersionId', 'ruleSetVersionId', 'environmentCode', 'status', 'version', 'contentHash', 'createdBy', 'createTime', 'allStaticChecksPassed', 'waitingForShadow', 'frozenComposition', 'stateChanges', 'staticChecks']); const summary = decodeCandidateSummary(Object.fromEntries(Object.entries(source).filter(([key]) => !['waitingForShadow', 'frozenComposition', 'stateChanges', 'staticChecks'].includes(key)))); const result: TestCandidateDetail = { ...summary, waitingForShadow: booleanValue(source.waitingForShadow), frozenComposition: decodeComposition(source.frozenComposition), stateChanges: arrayValue(source.stateChanges, decodeState), staticChecks: arrayValue(source.staticChecks, decodeCheck) }; validateCandidateDetailSemantics(result); return result; }
function decodeInitial(value: unknown): InitialStateChange { const source = exactRecord(value, ['auditEventId', 'eventTime', 'fromStatus', 'toStatus', 'reasonCode', 'triggerType', 'actorId', 'aggregateVersion']); const reasonCode = stringValue(source.reasonCode, codes, 64); if (reasonCode !== 'CANDIDATE_CREATED') fail(); return { auditEventId: stringValue(source.auditEventId, decimalId, 30), eventTime: timestamp(source.eventTime), fromStatus: source.fromStatus === null ? null : fail(), toStatus: enumValue(source.toStatus, ['DRAFT'] as const), reasonCode, triggerType: enumValue(source.triggerType, ['OPERATIONS_COMMAND'] as const), actorId: stringValue(source.actorId, decimalId, 30), aggregateVersion: source.aggregateVersion === 1 ? 1 : fail() }; }
export function decodeCreateCandidateResponse(value: unknown): CreateTestCandidateResponse { const source = exactRecord(value, ['releaseId', 'releaseNo', 'decisionPointId', 'pointCode', 'pointVersionId', 'ruleSetVersionId', 'environmentCode', 'status', 'version', 'contentHash', 'createdBy', 'createTime', 'initialStateChange']); const initialStateChange = decodeInitial(source.initialStateChange); if (source.version !== 1) fail(); const result = { releaseId: stringValue(source.releaseId, decimalId, 30), releaseNo: positive(source.releaseNo), decisionPointId: stringValue(source.decisionPointId, decimalId, 30), pointCode: stringValue(source.pointCode, codes, 64), pointVersionId: stringValue(source.pointVersionId, decimalId, 30), ruleSetVersionId: stringValue(source.ruleSetVersionId, decimalId, 30), environmentCode: enumValue(source.environmentCode, ['TEST'] as const), status: enumValue(source.status, ['DRAFT'] as const), version: 1 as const, contentHash: stringValue(source.contentHash, hash, 64), createdBy: stringValue(source.createdBy, decimalId, 30), createTime: timestamp(source.createTime), initialStateChange }; if (initialStateChange.actorId !== result.createdBy) fail(); return result; }
function decodeValidationCheck(value: unknown): ValidationCheck {
  const source = exactRecord(value, ['checkCode', 'checkStatus', 'severity', 'errorCode', 'errorSummary']);
  return { checkCode: stringValue(source.checkCode, codes, 64), checkStatus: enumValue(source.checkStatus, ['PASSED', 'FAILED'] as const), severity: enumValue(source.severity, ['INFO', 'ERROR'] as const), errorCode: nullableString(source.errorCode, codes, 64), errorSummary: source.errorSummary === null ? null : text(source.errorSummary, 500) };
}
function validateValidationChecks(checks: readonly ValidationCheck[]) {
  if (checks.length !== 8 || checks.some((check, index) => check.checkCode !== checkOrder[index])) fail();
  for (const check of checks) if (check.checkStatus === 'PASSED' ? check.severity !== 'INFO' || check.errorCode !== null || check.errorSummary !== null : check.severity !== 'ERROR' || check.errorCode === null || check.errorSummary === null) fail();
}
export function decodeValidateCandidateResponse(value: unknown): ValidateTestCandidateResponse {
  const source = exactRecord(value, ['releaseId', 'releaseNo', 'environmentCode', 'status', 'version', 'contentHash', 'allStaticChecksPassed', 'checks']);
  const status = enumValue(source.status, ['VALIDATING', 'REJECTED'] as const); const version = source.version === 2 || source.version === 3 ? source.version : fail(); const checks = arrayValue(source.checks, decodeValidationCheck); const allStaticChecksPassed = booleanValue(source.allStaticChecksPassed);
  validateValidationChecks(checks);
  if ((status === 'VALIDATING' && (version !== 2 || !allStaticChecksPassed || checks.some(check => check.checkStatus !== 'PASSED'))) || (status === 'REJECTED' && (version !== 3 || allStaticChecksPassed || !checks.some(check => check.checkStatus === 'FAILED')))) fail();
  return { releaseId: stringValue(source.releaseId, decimalId, 30), releaseNo: positive(source.releaseNo), environmentCode: enumValue(source.environmentCode, ['TEST'] as const), status, version, contentHash: stringValue(source.contentHash, hash, 64), allStaticChecksPassed, checks };
}
