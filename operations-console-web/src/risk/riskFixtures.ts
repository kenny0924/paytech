import type { CreateRuleSetVersionRequest, CreateRuleSetVersionResponse, CreateTestCandidateRequest, CreateTestCandidateResponse, DecisionActionDescriptor, DecisionInputFieldDescriptor, InitialStateChange, ReleaseStateChange, ReleaseStaticCheck, RuleSetVersionSummary, RuleSetListItem, TestCandidateListItem, ValueType, FreezeRuleSetVersionRequest, FreezeRuleSetVersionResponse, RuleAuthoringContext, RuleDefinition, RuleOperand, TestCandidateDetail, ValidateTestCandidateRequest, ValidateTestCandidateResponse } from './riskApiModels.ts';

export const hex = 'a'.repeat(64);
export const instant = '2026-09-07T10:20:30.123456Z';
const action: DecisionActionDescriptor = { actionCode: 'ALLOW_CHECKOUT', actionName: 'Allow Checkout', actionCategory: 'DECISION', versionNo: 1, terminalFlag: true, decisionCode: 'ALLOW', mergePolicy: 'MOST_RESTRICTIVE', shadowSupportedFlag: true, status: 'ACTIVE' };
const field = (fieldCode: string, valueType: ValueType): DecisionInputFieldDescriptor => ({ fieldCode, valueType, requiredFlag: true, dataClassification: 'INTERNAL', ruleReferenceFlag: true, actionParameterFlag: false });
export const authoringContext: RuleAuthoringContext = { descriptorCode: 'CHECKOUT_PREPARE_AUTHORING_V1', point: { pointCode: 'CHECKOUT_PREPARE', pointName: 'Checkout Prepare', businessDomain: 'CHECKOUT', inputContractCode: 'ORDER_PREPARATION', inputContractVersion: 4, outputContractCode: 'RISK_DECISION_RESULT', defaultDecision: 'REVIEW', evaluationStrategy: 'FIRST_TERMINAL', failureStrategy: 'FAIL_CLOSED', timeoutMillis: 500, status: 'ACTIVE' }, fields: [field('country_code', 'STRING'), field('currency', 'STRING'), field('discount_amount', 'NUMBER'), field('entity_source_type', 'STRING'), field('industry_code', 'STRING'), field('locale', 'STRING'), field('mcc_code', 'STRING'), field('merchandise_amount', 'NUMBER'), field('net_amount', 'NUMBER')], operators: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL'], actions: [action, { ...action, actionCode: 'DENY_CHECKOUT', actionName: 'Deny Checkout', decisionCode: 'DENY' }, { ...action, actionCode: 'REVIEW_CHECKOUT', actionName: 'Review Checkout', decisionCode: 'REVIEW' }], features: [], actionParameters: [], targetBindings: [], obligations: [], limitBindings: [] };
export const operand = (operandRole: 'LEFT' | 'RIGHT', sourceType: 'CONTEXT' | 'LITERAL', sequence: number, fieldCode: string | null, literalValue: string | boolean | null): RuleOperand => ({ operandRole, valueSequence: sequence, sourceType, fieldCode, valueType: 'STRING', literalValue });
export const rule: RuleDefinition = { ruleCode: 'RULE_ONE', ruleName: 'Rule one', priorityNo: 1, enabledFlag: true, reasonCode: null, reasonMessage: null, additionalActions: [], conditionNodes: [{ nodeKey: '1', parentNodeKey: null, sequenceNo: 1, nodeType: 'PREDICATE', groupOperator: null, comparisonOperator: 'EQ', operands: [operand('LEFT', 'CONTEXT', 1, 'country_code', null), operand('RIGHT', 'LITERAL', 1, null, 'AE')] }], action: { actionCode: 'ALLOW_CHECKOUT', actionVersionNo: 1 } };
const ruleSetSummary: RuleSetVersionSummary = { ruleSetVersionId: '1', pointVersionId: '2', pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'RULE_SET', ruleSetName: 'Rule set', versionNo: 1, status: 'DRAFT', contentHash: hex, createdBy: '3', changeReason: 'Initial version', createTime: instant, frozenBy: null, frozenTime: null };
export const ruleSet: RuleSetListItem = { ...ruleSetSummary, ruleSetId: '100', managementVersion: '1', inUse: false };
export const deleteRuleSetResponse = { ruleSetId: '100', status: 'DELETED' as const, version: '2', deletedBy: '3', deletedTime: instant };
export const candidate: TestCandidateListItem = { releaseId: '4', releaseNo: 1, decisionPointId: '5', pointCode: 'CHECKOUT_PREPARE', pointVersionId: '2', ruleSetVersionId: '1', environmentCode: 'TEST', status: 'DRAFT', version: 1, contentHash: hex, createdBy: '3', createTime: instant, allStaticChecksPassed: null };
export const initial: InitialStateChange = { auditEventId: '6', eventTime: instant, fromStatus: null, toStatus: 'DRAFT', reasonCode: 'CANDIDATE_CREATED', triggerType: 'OPERATIONS_COMMAND', actorId: '3', aggregateVersion: 1 };
export const checkCodes = ['REFERENCE_INTEGRITY', 'AST_SHAPE', 'AST_TYPES', 'FEATURE_DEPENDENCY', 'HANDLER_HASH', 'ACTION_PARAMETERS', 'TERMINAL_ACTION', 'SYSTEM_OBLIGATION'];
export const passedChecks: ReleaseStaticCheck[] = checkCodes.map((checkCode, index) => ({ checkCode, checkStatus: 'PASSED', severity: 'INFO', errorCode: null, errorSummary: null, auditEventId: String(index + 10), eventTime: instant }));

export const ruleSetDetail = { ...ruleSetSummary, rules: [rule] };
export const createRuleSetRequest: CreateRuleSetVersionRequest = {
  pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'RULE_SET', ruleSetName: '规则集', baseRuleSetVersionId: null,
  baseContentHash: null, changeReason: 'Initial version', rules: [rule],
};
export const freezeRuleSetRequest: FreezeRuleSetVersionRequest = { expectedContentHash: hex };
export const createCandidateRequest: CreateTestCandidateRequest = { ruleSetVersionId: '1', expectedRuleSetContentHash: hex };
export const validateCandidateRequest: ValidateTestCandidateRequest = { expectedVersion: 1 };
export const ruleSetPage = { items: [ruleSet], page: 1, size: 10, total: 1 };
export const candidatePage = { items: [candidate], page: 1, size: 10, total: 1 };
export const createRuleSetResponse: CreateRuleSetVersionResponse = {
  ruleSetVersionId: '1', pointVersionId: '2', ruleSetCode: 'RULE_SET', versionNo: 1,
  status: 'DRAFT', contentHash: hex, createdBy: '3', createTime: instant,
};
export const freezeRuleSetResponse: FreezeRuleSetVersionResponse = {
  ruleSetVersionId: '1', pointVersionId: '2', ruleSetCode: 'RULE_SET', versionNo: 1,
  status: 'FROZEN', contentHash: hex, frozenBy: '3', frozenTime: instant,
};
export const createCandidateResponse: CreateTestCandidateResponse = {
  releaseId: '4', releaseNo: 1, decisionPointId: '5', pointCode: 'CHECKOUT_PREPARE', pointVersionId: '2',
  ruleSetVersionId: '1', environmentCode: 'TEST', status: 'DRAFT', version: 1,
  contentHash: hex, createdBy: '3', createTime: instant,
  initialStateChange: { ...initial, fromStatus: null, toStatus: 'DRAFT', triggerType: 'OPERATIONS_COMMAND', aggregateVersion: 1 },
};

export function candidateDetail(state: 'draft' | 'pending' | 'passed' | 'rejected' = 'draft'): TestCandidateDetail {
  const stateChanges = [{ ...initial, triggerId: 'abcdef'.repeat(10) + 'abcd' }];
  const started: ReleaseStateChange = { auditEventId: '7', eventTime: instant, fromStatus: 'DRAFT', toStatus: 'VALIDATING',
    reasonCode: 'STATIC_VALIDATION_STARTED', triggerType: 'OPERATIONS_COMMAND', triggerId: 'b'.repeat(64), actorId: '9', aggregateVersion: 2 };
  const rejected: ReleaseStateChange = { ...started, auditEventId: '8', fromStatus: 'VALIDATING', toStatus: 'REJECTED', reasonCode: 'STATIC_VALIDATION_FAILED', aggregateVersion: 3 };
  const staticChecks = state === 'passed' || state === 'rejected' ? structuredClone(passedChecks) : [];
  if (state === 'rejected') Object.assign(staticChecks[0]!, { checkStatus: 'FAILED', severity: 'ERROR', errorCode: 'INVALID_REFERENCE', errorSummary: 'Reference no longer exists' });
  return {
    ...candidate, status: state === 'draft' ? 'DRAFT' : state === 'rejected' ? 'REJECTED' : 'VALIDATING',
    version: state === 'draft' ? 1 : state === 'rejected' ? 3 : 2,
    allStaticChecksPassed: state === 'passed' ? true : state === 'rejected' ? false : null,
    waitingForShadow: state === 'passed', frozenComposition: {
      decisionPointId: '5', pointCode: 'CHECKOUT_PREPARE', pointVersionId: '2', pointVersionNo: 1,
      pointVersionContentHash: hex, ruleSetVersionId: '1', ruleSetCode: 'RULE_SET', ruleSetVersionNo: 1, ruleSetContentHash: hex,
    }, stateChanges: state === 'draft' ? stateChanges : state === 'rejected' ? [...stateChanges, started, rejected] : [...stateChanges, started], staticChecks,
  };
}
export function validateCandidateResponse(rejected = false): ValidateTestCandidateResponse {
  return { releaseId: '4', releaseNo: 1, environmentCode: 'TEST', status: rejected ? 'REJECTED' : 'VALIDATING',
    version: rejected ? 3 : 2, contentHash: hex, allStaticChecksPassed: !rejected,
    checks: candidateDetail(rejected ? 'rejected' : 'passed').staticChecks.map(({ checkCode, checkStatus, severity, errorCode, errorSummary }) => ({ checkCode, checkStatus, severity, errorCode, errorSummary })),
  };
}

export function predicateRule(valueType: RuleOperand['valueType'], operator: string, contextSide: 'LEFT' | 'RIGHT', values: readonly unknown[]): RuleDefinition {
  return { ...structuredClone(rule), conditionNodes: [{ ...rule.conditionNodes[0], comparisonOperator: operator, operands: [
    { ...operand(contextSide, 'CONTEXT', 1, 'field_name', null), valueType },
    ...values.map((literalValue, index) => ({ ...operand(contextSide === 'LEFT' ? 'RIGHT' : 'LEFT', 'LITERAL', index + 1, null, null), valueType, literalValue })),
  ] }] } as RuleDefinition;
}

export type WirePath = readonly (string | number)[];
export function atPath(value: unknown, path: WirePath): unknown {
  return path.reduce<unknown>((current, key) => (current as Record<string | number, unknown>)[key], value);
}
export function changed(value: unknown, path: WirePath, replacement: unknown): unknown {
  const copy = structuredClone(value);
  (atPath(copy, path.slice(0, -1)) as Record<string | number, unknown>)[path.at(-1)!] = replacement;
  return copy;
}
export function omitted(value: unknown, path: WirePath): unknown {
  const copy = structuredClone(value);
  delete (atPath(copy, path.slice(0, -1)) as Record<string | number, unknown>)[path.at(-1)!];
  return copy;
}

export const configurationContext: RuleAuthoringContext = {
  ...authoringContext,
  fields: [{ fieldCode: 'client_id', valueType: 'STRING', requiredFlag: true, dataClassification: 'INTERNAL', ruleReferenceFlag: false, actionParameterFlag: true }, ...authoringContext.fields],
  actions: [
    { actionCode: 'ADD_RISK_TAG', actionName: '添加主体标签', actionCategory: 'TAG', versionNo: 1, terminalFlag: false, decisionCode: null, mergePolicy: 'UNION', shadowSupportedFlag: false, status: 'CONFIGURATION_ONLY' },
    authoringContext.actions[0]!,
    { actionCode: 'APPLY_COOLDOWN', actionName: '冷却', actionCategory: 'RESTRICTION', versionNo: 1, terminalFlag: false, decisionCode: null, mergePolicy: 'MAX', shadowSupportedFlag: false, status: 'CONFIGURATION_ONLY' },
    ...authoringContext.actions.slice(1),
  ],
  targetBindings: [{ bindingCode: 'CURRENT_CLIENT_V1', displayName: '当前 Client', subjectType: 'CLIENT', requiredFields: [{ role: 'SUBJECT_ID', fieldCode: 'client_id' }] }],
  actionParameters: ['APPLY_COOLDOWN', 'ADD_RISK_TAG'].flatMap(actionCode => [
    { actionCode, actionVersionNo: 1, parameterCode: 'SUBJECT_ID', valueType: 'STRING', requiredFlag: true, allowedSourceType: 'CONTEXT', targetParameterFlag: true, maxValues: 1, minimumValue: null, maximumValue: null, valuePattern: null },
    { actionCode, actionVersionNo: 1, parameterCode: actionCode === 'APPLY_COOLDOWN' ? 'DURATION' : 'TAG', valueType: actionCode === 'APPLY_COOLDOWN' ? 'DURATION' : 'STRING', requiredFlag: true, allowedSourceType: 'LITERAL', targetParameterFlag: false, maxValues: actionCode === 'APPLY_COOLDOWN' ? 1 : 32, minimumValue: actionCode === 'APPLY_COOLDOWN' ? '1' : null, maximumValue: actionCode === 'APPLY_COOLDOWN' ? '31536000000' : null, valuePattern: actionCode === 'ADD_RISK_TAG' ? '[A-Z][A-Z0-9_]{0,63}' : null },
  ]),
};
