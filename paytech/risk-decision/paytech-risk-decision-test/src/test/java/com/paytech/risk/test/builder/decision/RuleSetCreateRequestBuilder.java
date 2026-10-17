package com.paytech.risk.test.builder.decision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameter;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.AdditionalAction;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleActionRequestBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;

/**
 * 保存规则集版本请求的测试 Builder
 * <li>{@link #template(String)} 为首个版本：统一变量格式、一条 INPUT_COUNTRY_CODE EQ "AE" 即 ALLOW_CHECKOUT 的规则、无附加动作。</li>
 * <li>规则、节点、操作数与附加动作由静态工厂组装；字段可显式设为 null 或非法值，{@link #build()} 不修复。</li>
 *
 * @author Kenny
 */
public final class RuleSetCreateRequestBuilder {
    public static final String VARIABLE_REFERENCE = "VARIABLE_REFERENCE";

    public static final String ALLOW = "ALLOW_CHECKOUT";

    public static final String DENY = "DENY_CHECKOUT";

    public static final String REVIEW = "REVIEW_CHECKOUT";

    public static final String COOLDOWN = "APPLY_COOLDOWN";

    public static final String TAG = "ADD_RISK_TAG";

    /** 种子数据中 CURRENT_CLIENT_V1 主体绑定对应的 INPUT 变量。 */
    public static final String CLIENT_VARIABLE = "INPUT_CLIENT_ID";

    public static final String CLIENT_TARGET = "CURRENT_CLIENT_V1";

    private String pointCode = RuleSetBuilder.POINT_CODE;

    private String ruleSetCode;

    private String ruleSetName = "Checkout country rules";

    private String baseRuleSetVersionId;

    private String baseContentHash;

    private String changeReason = "Initial country rules";

    private List<RuleRequestBody> rules = new ArrayList<>(List.of(countryAllow()));

    private String operandFormat = VARIABLE_REFERENCE;

    private RuleSetCreateRequestBuilder() {}

    /** 首个版本的最小合法请求。 */
    public static RuleSetCreateRequestBuilder template(String ruleSetCode) {
        RuleSetCreateRequestBuilder builder = new RuleSetCreateRequestBuilder();
        builder.ruleSetCode = ruleSetCode;
        return builder;
    }

    public RuleSetCreateRequestBuilder pointCode(String pointCode) {
        this.pointCode = pointCode;
        return this;
    }

    public RuleSetCreateRequestBuilder ruleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
        return this;
    }

    /** 编辑基准版本与其内容摘要。 */
    public RuleSetCreateRequestBuilder base(String baseRuleSetVersionId, String baseContentHash) {
        this.baseRuleSetVersionId = baseRuleSetVersionId;
        this.baseContentHash = baseContentHash;
        return this;
    }

    public RuleSetCreateRequestBuilder changeReason(String changeReason) {
        this.changeReason = changeReason;
        return this;
    }

    /** 替换全部规则；null 表示缺少规则数组。 */
    public RuleSetCreateRequestBuilder rules(RuleRequestBody... rules) {
        this.rules = rules == null ? null : new ArrayList<>(Arrays.asList(rules));
        return this;
    }

    public RuleSetCreateRequestBuilder operandFormat(String operandFormat) {
        this.operandFormat = operandFormat;
        return this;
    }

    public CreateRuleSetVersionRequestBody build() {
        return new CreateRuleSetVersionRequestBody(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId,
            baseContentHash, changeReason, rules, operandFormat);
    }

    /** 模板规则：INPUT_COUNTRY_CODE EQ "AE" 时 ALLOW_CHECKOUT。 */
    public static RuleRequestBody countryAllow() {
        return rule("COUNTRY_ALLOW", "Country allow", 10, action(ALLOW),
            predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                literal("RIGHT", 1, "STRING", "AE")));
    }

    /** 启用、无原因代码、无附加动作的规则。 */
    public static RuleRequestBody rule(String ruleCode, String ruleName, int priorityNo, RuleActionRequestBody action,
        ConditionNodeRequestBody... nodes) {
        return rule(ruleCode, ruleName, priorityNo, null, null, action, List.of(), nodes);
    }

    /** 完整规则字段。 */
    public static RuleRequestBody rule(String ruleCode, String ruleName, int priorityNo, String reasonCode,
        String reasonMessage, RuleActionRequestBody action, List<AdditionalAction> additionalActions,
        ConditionNodeRequestBody... nodes) {
        return new RuleRequestBody(ruleCode, ruleName, priorityNo, true, reasonCode, List.of(nodes), action,
            reasonMessage, additionalActions);
    }

    /** 准确版本 1 的终态动作。 */
    public static RuleActionRequestBody action(String actionCode) {
        return new RuleActionRequestBody(actionCode, 1);
    }

    /** 谓词节点。 */
    public static ConditionNodeRequestBody predicate(String nodeKey, String parentNodeKey, int sequenceNo,
        String comparisonOperator, OperandRequestBody... operands) {
        return new ConditionNodeRequestBody(nodeKey, parentNodeKey, sequenceNo, "PREDICATE", null, comparisonOperator,
            List.of(operands));
    }

    /** 分组节点，无操作数。 */
    public static ConditionNodeRequestBody group(String nodeKey, String parentNodeKey, int sequenceNo,
        String groupOperator) {
        return new ConditionNodeRequestBody(nodeKey, parentNodeKey, sequenceNo, "GROUP", groupOperator, null,
            List.of());
    }

    /** 统一变量引用操作数。 */
    public static OperandRequestBody variable(String role, int valueSequence, String variableCode, String valueType) {
        return new OperandRequestBody(role, valueSequence, "VARIABLE", null, valueType, null, variableCode, null, null,
            null);
    }

    /** 字面量操作数；NUMBER 默认 SCALAR 量纲。 */
    public static OperandRequestBody literal(String role, int valueSequence, String valueType, Object value) {
        return new OperandRequestBody(role, valueSequence, "LITERAL", null, valueType, value, null,
            "NUMBER".equals(valueType) ? "SCALAR" : null, null, null);
    }

    /** 以当前 Client 为主体的冷却附加动作，时长为毫秒字符串。 */
    public static AdditionalAction cooldown(int sequenceNo, String durationMillis) {
        return new AdditionalAction(sequenceNo, COOLDOWN, 1, CLIENT_TARGET,
            List.of(new ActionParameter("DURATION", 1, "LITERAL", null, "DURATION", durationMillis)),
            CLIENT_VARIABLE);
    }

    /** 以当前 Client 为主体的打标签附加动作。 */
    public static AdditionalAction tag(int sequenceNo, String... tags) {
        List<ActionParameter> parameters = new ArrayList<>();
        for (String value : tags) {
            parameters.add(new ActionParameter("TAG", parameters.size() + 1, "LITERAL", null, "STRING", value));
        }
        return new AdditionalAction(sequenceNo, TAG, 1, CLIENT_TARGET, parameters, CLIENT_VARIABLE);
    }
}
