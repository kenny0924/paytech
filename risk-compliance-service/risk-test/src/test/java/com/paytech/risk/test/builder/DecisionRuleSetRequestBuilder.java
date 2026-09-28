package com.paytech.risk.test.builder;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import java.util.List;

/** 构造最小合法的 CHECKOUT_PREPARE Rule Set 请求。 */
public final class DecisionRuleSetRequestBuilder {

    private DecisionRuleSetRequestBuilder() {
    }

    public static CreateRuleSetVersionRequestBody firstVersion(String ruleSetCode) {
        return firstVersion(ruleSetCode, "AE");
    }

    /** 当前保存合同；firstVersion 保留原 Wire，供已持久化历史回放使用。 */
    public static CreateRuleSetVersionRequestBody currentFirstVersion(String code) {
        return current(firstVersion(code));
    }

    public static CreateRuleSetVersionRequestBody currentFirstVersion(String code, String country) {
        return current(firstVersion(code, country));
    }

    public static CreateRuleSetVersionRequestBody currentNextVersion(String code, String versionId, String hash) {
        return current(nextVersion(code, versionId, hash));
    }

    public static CreateRuleSetVersionRequestBody currentFirstVersionWithChangeReason(String code, String reason) {
        return current(firstVersionWithChangeReason(code, reason));
    }

    public static CreateRuleSetVersionRequestBody current(CreateRuleSetVersionRequestBody old) {
        var rules = old.rules().stream().map(rule -> new RuleRequestBody(rule.ruleCode(), rule.ruleName(),
            rule.priorityNo(), rule.enabledFlag(), rule.reasonCode(), rule.conditionNodes().stream().map(node ->
                new ConditionNodeRequestBody(node.nodeKey(), node.parentNodeKey(), node.sequenceNo(), node.nodeType(),
                    node.groupOperator(), node.comparisonOperator(), node.operands().stream().map(operand ->
                        new OperandRequestBody(operand.operandRole(), operand.valueSequence(),
                            operand.sourceType().equals("CONTEXT") ? "VARIABLE" : operand.sourceType(), null,
                            operand.valueType(), operand.literalValue(), operand.fieldCode() == null ? null
                                : "INPUT_" + operand.fieldCode().toUpperCase(java.util.Locale.ROOT),
                            operand.valueType().equals("NUMBER") && operand.sourceType().equals("LITERAL") ? "SCALAR" : null,
                            null, null)).toList())).toList(), rule.action(), rule.reasonMessage(),
            rule.additionalActions() == null ? List.of() : rule.additionalActions())).toList();
        return new CreateRuleSetVersionRequestBody(old.pointCode(), old.ruleSetCode(), old.ruleSetName(),
            old.baseRuleSetVersionId(), old.baseContentHash(), old.changeReason(), rules, "VARIABLE_REFERENCE");
    }

    public static CreateRuleSetVersionRequestBody firstVersion(String ruleSetCode, String countryCode) {
        return version(ruleSetCode, "Auto Checkout Rule Set", null, null,
            "验证真实 MySQL 完整快照", countryCode);
    }

    public static CreateRuleSetVersionRequestBody firstVersionWithChangeReason(
        String ruleSetCode,
        String changeReason
    ) {
        return version(ruleSetCode, "Auto Checkout Rule Set", null, null, changeReason, "AE");
    }

    public static CreateRuleSetVersionRequestBody nextVersion(
        String ruleSetCode,
        String baseRuleSetVersionId,
        String baseContentHash
    ) {
        return version(ruleSetCode, "Auto Checkout Rule Set V2", baseRuleSetVersionId, baseContentHash,
            "验证不可变后续版本", "SA");
    }

    private static CreateRuleSetVersionRequestBody version(
        String ruleSetCode,
        String ruleSetName,
        String baseRuleSetVersionId,
        String baseContentHash,
        String changeReason,
        String countryCode
    ) {
        OperandRequestBody left = new OperandRequestBody(
            "LEFT", 1, "CONTEXT", "country_code", "STRING", null);
        OperandRequestBody right = new OperandRequestBody(
            "RIGHT", 1, "LITERAL", null, "STRING", countryCode);
        ConditionNodeRequestBody condition = new ConditionNodeRequestBody(
            "root", null, 1, "PREDICATE", null, "EQ", List.of(left, right));
        RuleRequestBody rule = new RuleRequestBody(
            "COUNTRY_ALLOW", "Country Allow", 10, true, "ALLOW_REASON", List.of(condition),
            new RuleActionRequestBody("ALLOW_CHECKOUT", 1));
        return new CreateRuleSetVersionRequestBody(
            "CHECKOUT_PREPARE", ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash,
            changeReason, List.of(rule));
    }
}
