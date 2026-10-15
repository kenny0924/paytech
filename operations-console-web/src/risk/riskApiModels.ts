import type { VariableProjection, VariableQueryContext } from './riskVariable.ts';
export type RuleSetStatus = 'DRAFT' | 'FROZEN' | 'RETIRED';
export type CandidateStatus = 'DRAFT' | 'VALIDATING' | 'REJECTED';
export type OperatorCode = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'IN' | 'NOT_IN' | 'BETWEEN' | 'IS_NULL' | 'IS_NOT_NULL';
export type ValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATETIME' | 'DURATION';

export interface DecisionActionDescriptor { actionCode: string; actionName: string; actionCategory: string; versionNo: number; terminalFlag: boolean; decisionCode: string | null; mergePolicy: string; shadowSupportedFlag: boolean; status: string; }
export interface DecisionPointDescriptor { pointCode: string; pointName: string; businessDomain: string; inputContractCode: string; inputContractVersion: number; outputContractCode: string; defaultDecision: string; evaluationStrategy: string; failureStrategy: string; timeoutMillis: number; status: string; }
export interface DecisionInputFieldDescriptor { variableCode?: string | null; fieldCode: string; valueType: ValueType; requiredFlag: boolean; dataClassification: string; ruleReferenceFlag: boolean; actionParameterFlag: boolean; }
export interface RuleAuthoringContext { operandFormat?: string; variableQueryContext?: VariableQueryContext; descriptorCode: string; point: DecisionPointDescriptor; fields: readonly DecisionInputFieldDescriptor[]; operators: readonly OperatorCode[]; actions: readonly DecisionActionDescriptor[]; features: readonly never[]; actionParameters: readonly ActionParameterDescriptor[]; targetBindings: readonly TargetBindingDescriptor[]; obligations: readonly never[]; limitBindings: readonly never[]; }
export interface RuleOperand { operandRole: 'LEFT' | 'RIGHT'; valueSequence: number; sourceType: 'CONTEXT' | 'VARIABLE' | 'LITERAL'; fieldCode: string | null; valueType: ValueType; literalValue: string | boolean | null; variableCode?: string | null; numericUnit?: 'SCALAR' | 'MONEY' | null; currencyCode?: string | null; currencyVariableCode?: string | null; }
export interface RuleConditionNode { nodeKey: string; parentNodeKey: string | null; sequenceNo: number; nodeType: 'GROUP' | 'PREDICATE'; groupOperator: 'AND' | 'OR' | 'NOT' | null; comparisonOperator: OperatorCode | null; operands: readonly RuleOperand[]; }
export interface RuleAction { actionCode: string; actionVersionNo: number; }
export interface RuleDefinition { ruleCode: string; ruleName: string; priorityNo: number; enabledFlag: boolean; reasonCode: string | null; conditionNodes: readonly RuleConditionNode[]; action: RuleAction | null; reasonMessage: string | null; additionalActions: readonly AdditionalAction[]; }
export interface CreateRuleSetVersionRequest { operandFormat?: string; pointCode: string; ruleSetCode: string; ruleSetName: string; baseRuleSetVersionId: string | null; baseContentHash: string | null; changeReason: string; rules: readonly RuleDefinition[]; }
export interface FreezeRuleSetVersionRequest { expectedContentHash: string; }
export interface CreateTestCandidateRequest { ruleSetVersionId: string; expectedRuleSetContentHash: string; }
export interface ValidateTestCandidateRequest { expectedVersion: number; }
export interface RuleSetVersionSummary { ruleSetVersionId: string; pointVersionId: string | null; pointCode: string; ruleSetCode: string; ruleSetName: string; versionNo: number; status: RuleSetStatus; contentHash: string; createdBy: string; changeReason: string; createTime: string; frozenBy: string | null; frozenTime: string | null; }
export interface RuleSetListItem extends RuleSetVersionSummary { ruleSetId: string; managementVersion: string; inUse: boolean; }
export interface DeleteRuleSetRequest { expectedVersion: string; }
export interface DeleteRuleSetResponse { ruleSetId: string; status: 'DELETED'; version: string; deletedBy: string; deletedTime: string; }
export interface RuleSetDetail extends RuleSetVersionSummary, VariableProjection { operandFormat?: string | null; rules: readonly RuleDefinition[]; }
export interface CreateRuleSetVersionResponse { ruleSetVersionId: string; pointVersionId: string | null; ruleSetCode: string; versionNo: number; status: 'DRAFT'; contentHash: string; createdBy: string; createTime: string; }
export interface FreezeRuleSetVersionResponse { ruleSetVersionId: string; pointVersionId: string | null; ruleSetCode: string; versionNo: number; status: 'FROZEN'; contentHash: string; frozenBy: string; frozenTime: string; }
export interface TestCandidateSummary { releaseId: string; releaseNo: number; decisionPointId: string; pointCode: string; pointVersionId: string; ruleSetVersionId: string; environmentCode: 'TEST'; status: CandidateStatus; version: number; contentHash: string; createdBy: string; createTime: string; allStaticChecksPassed: boolean | null; }
export interface TestCandidateListItem extends TestCandidateSummary {}
export interface FrozenComposition { decisionPointId: string; pointCode: string; pointVersionId: string; pointVersionNo: number; pointVersionContentHash: string; ruleSetVersionId: string; ruleSetCode: string; ruleSetVersionNo: number; ruleSetContentHash: string; }
export interface ReleaseStateChange { auditEventId: string; eventTime: string; fromStatus: CandidateStatus | null; toStatus: CandidateStatus; reasonCode: string; triggerType: 'OPERATIONS_COMMAND'; triggerId: string; actorId: string; aggregateVersion: number; }
export interface ReleaseStaticCheck { auditEventId: string; eventTime: string; checkCode: string; checkStatus: 'PASSED' | 'FAILED'; severity: 'INFO' | 'ERROR'; errorCode: string | null; errorSummary: string | null; }
export interface TestCandidateDetail extends TestCandidateSummary { waitingForShadow: boolean; frozenComposition: FrozenComposition; stateChanges: readonly ReleaseStateChange[]; staticChecks: readonly ReleaseStaticCheck[]; }
export interface InitialStateChange { auditEventId: string; eventTime: string; fromStatus: null; toStatus: 'DRAFT'; reasonCode: string; triggerType: 'OPERATIONS_COMMAND'; actorId: string; aggregateVersion: 1; }
export interface ValidationCheck { checkCode: string; checkStatus: 'PASSED' | 'FAILED'; severity: 'INFO' | 'ERROR'; errorCode: string | null; errorSummary: string | null; }
export interface CreateTestCandidateResponse { releaseId: string; releaseNo: number; decisionPointId: string; pointCode: string; pointVersionId: string; ruleSetVersionId: string; environmentCode: 'TEST'; status: 'DRAFT'; version: 1; contentHash: string; createdBy: string; createTime: string; initialStateChange: InitialStateChange; }
export interface ValidateTestCandidateResponse { releaseId: string; releaseNo: number; environmentCode: 'TEST'; status: 'VALIDATING' | 'REJECTED'; version: 2 | 3; contentHash: string; allStaticChecksPassed: boolean; checks: readonly ValidationCheck[]; }

export interface TargetBindingDescriptor { subjectSemanticRole?: string | null; bindingCode: string; displayName: string; subjectType: string; requiredFields: readonly { role: string; fieldCode: string }[]; }
export interface ActionParameterDescriptor { actionCode: string; actionVersionNo: number; parameterCode: string; valueType: ValueType; requiredFlag: boolean; allowedSourceType: 'CONTEXT' | 'VARIABLE' | 'LITERAL'; targetParameterFlag: boolean; maxValues: number; minimumValue: string | null; maximumValue: string | null; valuePattern: string | null; }
export interface ActionParameter { parameterCode: string; valueSequence: number; sourceType: 'CONTEXT' | 'LITERAL'; fieldCode: string | null; valueType: ValueType; literalValue: string | boolean | null; }
export interface AdditionalAction { subjectVariableCode?: string | null; sequenceNo: number; actionCode: string; actionVersionNo: number; targetBindingCode: string; parameters: readonly ActionParameter[]; }
