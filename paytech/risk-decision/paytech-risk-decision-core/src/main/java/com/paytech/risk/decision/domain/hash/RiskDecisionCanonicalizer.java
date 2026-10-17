package com.paytech.risk.decision.domain.hash;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import java.util.ArrayList;
import java.util.List;

/** 仅暴露七类已冻结 Schema 的 canonicalization。 */
public interface RiskDecisionCanonicalizer {

    CanonicalHash ruleSetVersion(RuleSetVersionProjection projection);

    CanonicalHash decisionPointVersion(DecisionPointVersionProjection projection);

    CanonicalHash decisionRelease(DecisionReleaseProjection projection);

    CanonicalHash createRuleSetVersionCommand(CreateRuleSetVersionCommandProjection projection);

    CanonicalHash freezeRuleSetVersionCommand(FreezeRuleSetVersionCommandProjection projection);

    /** 只对新增删除命令进行域分隔，不改变历史 Hash。 */
    CanonicalHash deleteRuleSetCommand(DeleteRuleSetCommandProjection projection);

    record DeleteRuleSetCommandProjection(String ruleSetId, String expectedVersion) { }

    CanonicalHash createTestReleaseCommand(CreateTestReleaseCommandProjection projection);

    CanonicalHash validateTestReleaseCommand(ValidateTestReleaseCommandProjection projection);

    record CanonicalHash(String schema, byte[] canonicalUtf8, String sha256LowerHex) {
        public CanonicalHash {
            canonicalUtf8 = canonicalUtf8.clone();
        }

        @Override
        public byte[] canonicalUtf8() {
            return canonicalUtf8.clone();
        }
    }

    sealed interface LiteralProjection permits NullValue, StringValue, BooleanValue { }

    enum NullValue implements LiteralProjection { INSTANCE }

    record StringValue(String value) implements LiteralProjection { }

    record BooleanValue(boolean value) implements LiteralProjection { }

    sealed interface ConditionProjection permits GroupProjection, PredicateProjection { }

    /** children 必须是 AST 验证器按连续 sibling sequence 产生的规范顺序。 */
    record GroupProjection(GroupOperator groupOperator, List<ConditionProjection> children)
        implements ConditionProjection {
        public GroupProjection { children = immutable(children); }
    }

    record PredicateProjection(ComparisonOperator comparisonOperator, List<OperandProjection> operands)
        implements ConditionProjection {
        public PredicateProjection { operands = immutable(operands); }
    }

    record OperandProjection(OperandRole operandRole, int valueSequence, OperandSource sourceType,
                             String fieldCode, DecisionValueType valueType, LiteralProjection literalValue,
                             String variableCode, NumericUnit numericUnit, String currencyCode, String currencyVariableCode) {
        /** 旧内容精确保留原六字段投影。 */
        public OperandProjection(OperandRole operandRole, int valueSequence, OperandSource sourceType,
            String fieldCode, DecisionValueType valueType, LiteralProjection literalValue) {
            this(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue, null, null, null, null);
        }
    }

    record ActionProjection(String actionCode, int actionVersionNo, String handlerCode,
                            String implementationVersion, String implementationHash,
                            ActionCategory actionCategory, boolean terminalFlag, DecisionValue decisionCode,
                            MergePolicy mergePolicy, boolean shadowSupportedFlag) { }

    record RuleProjection(String ruleCode, String ruleName, int priorityNo, boolean enabledFlag,
                          String reasonCode, ConditionProjection condition, ActionProjection action,
                          String reasonMessage, List<AdditionalActionProjection> additionalActions) {
        public RuleProjection(String ruleCode, String ruleName, int priorityNo, boolean enabledFlag,
            String reasonCode, ConditionProjection condition, ActionProjection action) {
            this(ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, condition, action, null, List.of());
        }
    }

    record ParameterProjection(String parameterCode, int valueSequence, String sourceType, String fieldCode,
                               DecisionValueType valueType, LiteralProjection literalValue, String variableCode) {
        /** 旧参数不携带变量引用。 */
        public ParameterProjection(String parameterCode, int valueSequence, String sourceType, String fieldCode,
            DecisionValueType valueType, LiteralProjection literalValue) {
            this(parameterCode, valueSequence, sourceType, fieldCode, valueType, literalValue, null);
        }
    }

    record AdditionalActionProjection(int sequenceNo, String actionCode, int actionVersionNo,
                                      String targetBindingCode, List<ParameterProjection> parameters, String subjectVariableCode) {
        /** 旧动作主体不增加 Hash 字段。 */
        public AdditionalActionProjection(int sequenceNo, String actionCode, int actionVersionNo,
            String targetBindingCode, List<ParameterProjection> parameters) {
            this(sequenceNo, actionCode, actionVersionNo, targetBindingCode, parameters, null);
        }
        public AdditionalActionProjection { parameters = immutable(parameters); }
    }

    record RuleSetVersionProjection(String pointCode, String ruleSetCode, String ruleSetName, int versionNo,
                                    List<RuleProjection> rules, String contentFormat) {
        public RuleSetVersionProjection(String pointCode, String ruleSetCode, String ruleSetName,
            int versionNo, List<RuleProjection> rules) {
            this(pointCode, ruleSetCode, ruleSetName, versionNo, rules, "LEGACY");
        }
        public RuleSetVersionProjection { rules = immutable(rules); }
    }

    record InputFieldProjection(String fieldCode, DecisionValueType valueType, boolean requiredFlag,
                                DataClassification dataClassification, boolean ruleReferenceFlag,
                                boolean actionParameterFlag) { }

    record RuleSetBindingProjection(int sequenceNo, String ruleSetCode, int ruleSetVersionNo,
                                    String ruleSetContentHash) { }

    record UnsupportedProjection(String marker) { }

    record DecisionPointVersionProjection(String pointCode, DecisionBusinessDomain businessDomain,
                                          int pointVersionNo, String inputContractCode, int inputContractVersion,
                                          String outputContractCode, DecisionValue defaultDecision,
                                          EvaluationStrategy evaluationStrategy, FailureStrategy failureStrategy,
                                          int timeoutMillis, List<InputFieldProjection> inputFields,
                                          List<RuleSetBindingProjection> ruleSets,
                                          List<UnsupportedProjection> features,
                                          List<UnsupportedProjection> obligations,
                                          List<UnsupportedProjection> limitBindings) {
        public DecisionPointVersionProjection {
            inputFields = immutable(inputFields); ruleSets = immutable(ruleSets);
            features = immutable(features); obligations = immutable(obligations);
            limitBindings = immutable(limitBindings);
        }
    }

    record ReleaseDecisionPointProjection(String decisionPointId, String pointCode,
                                          DecisionBusinessDomain businessDomain) { }

    record ReleasePointVersionProjection(String decisionPointId, String pointCode, String pointVersionId,
                                         int pointVersionNo, String inputContractCode, int inputContractVersion,
                                         String outputContractCode, DecisionValue defaultDecision,
                                         EvaluationStrategy evaluationStrategy, FailureStrategy failureStrategy,
                                         int timeoutMillis, String contentHash) { }

    record ReleaseInputFieldProjection(String inputFieldId, String pointVersionId, String fieldCode,
                                       DecisionValueType valueType, boolean requiredFlag,
                                       DataClassification dataClassification, boolean ruleReferenceFlag,
                                       boolean actionParameterFlag) { }

    record ReleaseRuleSetBindingProjection(String bindingId, String pointVersionId, String ruleSetVersionId,
                                           int sequenceNo) { }

    record ReleaseRuleSetVersionProjection(String ruleSetVersionId, String decisionPointId, String ruleSetCode,
                                           int versionNo, String contentHash, String contentFormat) {
        public ReleaseRuleSetVersionProjection(String ruleSetVersionId, String decisionPointId, String ruleSetCode,
            int versionNo, String contentHash) {
            this(ruleSetVersionId, decisionPointId, ruleSetCode, versionNo, contentHash, "LEGACY");
        }
    }

    record ReleaseRuleProjection(String ruleId, String ruleSetVersionId, String ruleCode, int priorityNo,
                                 boolean enabledFlag, String reasonCode, String reasonMessage) {
        public ReleaseRuleProjection(String ruleId, String ruleSetVersionId, String ruleCode, int priorityNo,
            boolean enabledFlag, String reasonCode) {
            this(ruleId, ruleSetVersionId, ruleCode, priorityNo, enabledFlag, reasonCode, null);
        }
    }

    record ReleaseNodeProjection(String nodeId, String ruleId, String parentNodeId, NodeType nodeType,
                                 GroupOperator groupOperator, ComparisonOperator comparisonOperator,
                                 int sequenceNo) { }

    record ReleaseOperandProjection(String operandId, String nodeId, OperandRole operandRole, int valueSequence,
                                    OperandSource sourceType, String featureVersionId, String inputFieldId,
                                    DecisionValueType valueType, String stringValue, String numberValue,
                                    Boolean booleanValue, String datetimeValue, String durationValue) { }

    record ReleaseActionVersionProjection(String actionVersionId, String actionCode, ActionCategory actionCategory,
                                          int versionNo, String handlerCode, String implementationVersion,
                                          String implementationHash, boolean terminalFlag, DecisionValue decisionCode,
                                          MergePolicy mergePolicy, boolean shadowSupportedFlag) { }

    record ReleaseRuleActionProjection(String ruleActionId, String ruleId, String actionVersionId, int sequenceNo) { }

    record DecisionReleaseProjection(String releaseId, int releaseNo, String decisionPointId, String pointVersionId,
                                     DecisionEnvironment environmentCode,
                                     ReleaseDecisionPointProjection decisionPoint,
                                     ReleasePointVersionProjection pointVersion,
                                     List<ReleaseInputFieldProjection> inputFields,
                                     List<ReleaseRuleSetBindingProjection> ruleSetBindings,
                                     List<ReleaseRuleSetVersionProjection> ruleSetVersions,
                                     List<ReleaseRuleProjection> rules, List<ReleaseNodeProjection> nodes,
                                     List<ReleaseOperandProjection> operands,
                                     List<ReleaseActionVersionProjection> actionVersions,
                                     List<ReleaseRuleActionProjection> ruleActions,
                                     List<UnsupportedProjection> features,
                                     List<UnsupportedProjection> featureDependencies,
                                     List<UnsupportedProjection> actionParameterDefinitions,
                                     List<UnsupportedProjection> ruleActionParameters,
                                     List<UnsupportedProjection> obligations,
                                     List<UnsupportedProjection> limitBindings) {
        public DecisionReleaseProjection {
            inputFields = immutable(inputFields); ruleSetBindings = immutable(ruleSetBindings);
            ruleSetVersions = immutable(ruleSetVersions); rules = immutable(rules); nodes = immutable(nodes);
            operands = immutable(operands); actionVersions = immutable(actionVersions);
            ruleActions = immutable(ruleActions); features = immutable(features);
            featureDependencies = immutable(featureDependencies);
            actionParameterDefinitions = immutable(actionParameterDefinitions);
            ruleActionParameters = immutable(ruleActionParameters); obligations = immutable(obligations);
            limitBindings = immutable(limitBindings);
        }
    }

    record CommandActionProjection(String actionCode, int actionVersionNo) { }

    record CommandRuleProjection(String ruleCode, String ruleName, int priorityNo, boolean enabledFlag,
                                 String reasonCode, ConditionProjection condition, CommandActionProjection action,
                                 String reasonMessage, List<AdditionalActionProjection> additionalActions) {
        public CommandRuleProjection(String ruleCode, String ruleName, int priorityNo, boolean enabledFlag,
            String reasonCode, ConditionProjection condition, CommandActionProjection action) {
            this(ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, condition, action, null, List.of());
        }
    }

    record CreateRuleSetVersionCommandProjection(String pointCode, String ruleSetCode, String ruleSetName,
                                                 String baseRuleSetVersionId, String baseContentHash,
                                                 String changeReason, List<CommandRuleProjection> rules,
                                                 String contentFormat) {
        public CreateRuleSetVersionCommandProjection(String pointCode, String ruleSetCode, String ruleSetName,
            String baseRuleSetVersionId, String baseContentHash, String changeReason, List<CommandRuleProjection> rules) {
            this(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules, "LEGACY");
        }
        public CreateRuleSetVersionCommandProjection { rules = immutable(rules); }
    }

    record FreezeRuleSetVersionCommandProjection(String ruleSetVersionId, String expectedContentHash) { }

    record CreateTestReleaseCommandProjection(String ruleSetVersionId, String expectedRuleSetContentHash) { }

    record ValidateTestReleaseCommandProjection(String releaseId, int expectedVersion) { }

    private static <T> List<T> immutable(List<T> values) {
        if (values == null) {
            throw new InvalidParameterException("规范投影集合无效");
        }
        ArrayList<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                throw new InvalidParameterException("规范投影集合无效");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }
}
