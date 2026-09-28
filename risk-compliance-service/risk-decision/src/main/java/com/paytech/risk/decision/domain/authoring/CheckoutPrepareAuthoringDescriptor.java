package com.paytech.risk.decision.domain.authoring;

import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import java.util.List;
import java.util.Objects;

/** 旧协议和 Hash 的精确合同 Reader；新配置候选只从统一变量库读取。 */
public final class CheckoutPrepareAuthoringDescriptor {

    private static final CheckoutPrepareAuthoringDescriptor V1 = new CheckoutPrepareAuthoringDescriptor();

    private final DecisionPointDescriptor point;
    private final List<InputFieldDescriptor> fields;
    private final List<ComparisonOperator> operators;
    private final List<DecisionActionDescriptor> actions;

    private CheckoutPrepareAuthoringDescriptor() {
        point = new DecisionPointDescriptor("CHECKOUT_PREPARE", "Checkout Prepare", "CHECKOUT",
            "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT", DecisionValue.REVIEW,
            EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500, ConfigurationStatus.ACTIVE);
        fields = com.paytech.risk.decision.domain.variable.DecisionSourceContracts.all().stream()
            .filter(c -> c.sourceType() == com.paytech.risk.decision.domain.enums.VariableSource.INPUT
                && c.pointCode().equals(point.pointCode()) && c.contractCode().equals(point.inputContractCode())
                && c.contractVersion() == point.inputContractVersion()).findFirst().orElseThrow().fields().stream()
            .map(f -> new InputFieldDescriptor(f.fieldKey(), f.valueType(), f.required(), f.dataClassification(),
                f.allowedUsages().contains(com.paytech.risk.decision.domain.enums.VariableUsage.RULE_CONDITION),
                f.allowedUsages().contains(com.paytech.risk.decision.domain.enums.VariableUsage.ACTION_PARAMETER))).toList();
        operators = List.of(ComparisonOperator.EQ, ComparisonOperator.NE, ComparisonOperator.GT,
            ComparisonOperator.GTE, ComparisonOperator.LT, ComparisonOperator.LTE, ComparisonOperator.IN,
            ComparisonOperator.NOT_IN, ComparisonOperator.BETWEEN, ComparisonOperator.IS_NULL,
            ComparisonOperator.IS_NOT_NULL);
        actions = List.of(
            action("ALLOW_CHECKOUT", "Allow Checkout", "DECISION_ALLOW_V1", DecisionValue.ALLOW,
                "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1"),
            action("REVIEW_CHECKOUT", "Review Checkout", "DECISION_REVIEW_V1", DecisionValue.REVIEW,
                "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716"),
            action("DENY_CHECKOUT", "Deny Checkout", "DECISION_DENY_V1", DecisionValue.DENY,
                "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7")
        );
    }

    public static CheckoutPrepareAuthoringDescriptor v1() {
        return V1;
    }

    public String descriptorCode() {
        return "CHECKOUT_PREPARE_AUTHORING_V1";
    }

    public DecisionPointDescriptor point() {
        return point;
    }

    public List<InputFieldDescriptor> fields() {
        return fields.stream().filter(InputFieldDescriptor::ruleReferenceFlag).toList();
    }

    /** 按准确输入合同解释历史和新配置，不使用最新版本兜底。 */
    public List<InputFieldDescriptor> inputFields(String pointCode, String contractCode, int contractVersion) {
        if (!point.pointCode().equals(pointCode) || !point.inputContractCode().equals(contractCode)
            || point.inputContractVersion() != contractVersion) {
            throw new com.paytech.foundation.common.error.ErrorException("规则输入合同未登记");
        }
        return fields;
    }

    /** 当前创作合同的完整输入目录，包含仅动作可用字段。 */
    public List<InputFieldDescriptor> inputFields() {
        return inputFields(point.pointCode(), point.inputContractCode(), point.inputContractVersion());
    }

    public List<ComparisonOperator> operators() {
        return operators;
    }

    public List<DecisionActionDescriptor> actions() {
        return actions;
    }

    private static InputFieldDescriptor field(String code, DecisionValueType type) {
        return new InputFieldDescriptor(code, type, true, DataClassification.INTERNAL, true, false);
    }

    private static DecisionActionDescriptor action(
        String code, String name, String handler, DecisionValue decision, String hash
    ) {
        return new DecisionActionDescriptor(code, name, 1, handler, "1.0.0", hash,
            ActionCategory.DECISION, true, decision, MergePolicy.MOST_RESTRICTIVE, true,
            ConfigurationStatus.ACTIVE);
    }

    public record DecisionPointDescriptor(
        String pointCode,
        String pointName,
        String businessDomain,
        String inputContractCode,
        int inputContractVersion,
        String outputContractCode,
        DecisionValue defaultDecision,
        EvaluationStrategy evaluationStrategy,
        FailureStrategy failureStrategy,
        int timeoutMillis,
        ConfigurationStatus status
    ) {
        public DecisionPointDescriptor {
            Objects.requireNonNull(pointCode);
            Objects.requireNonNull(pointName);
            Objects.requireNonNull(businessDomain);
            Objects.requireNonNull(inputContractCode);
            Objects.requireNonNull(outputContractCode);
            Objects.requireNonNull(defaultDecision);
            Objects.requireNonNull(evaluationStrategy);
            Objects.requireNonNull(failureStrategy);
            Objects.requireNonNull(status);
        }
    }

    public record InputFieldDescriptor(
        String fieldCode,
        DecisionValueType valueType,
        boolean requiredFlag,
        DataClassification dataClassification,
        boolean ruleReferenceFlag,
        boolean actionParameterFlag
    ) { }

    public record DecisionActionDescriptor(
        String actionCode,
        String actionName,
        int versionNo,
        String handlerCode,
        String implementationVersion,
        String implementationHash,
        ActionCategory actionCategory,
        boolean terminalFlag,
        DecisionValue decisionCode,
        MergePolicy mergePolicy,
        boolean shadowSupportedFlag,
        ConfigurationStatus status
    ) { }
}
