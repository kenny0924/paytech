package com.paytech.risk.decision.domain.authoring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;

/**
 * 工程注册的决策点创作合同：输入字段、运算符和规则终态动作。
 * <li>结账（CHECKOUT_PREPARE）保持原合同、动作 ID 与 Handler 摘要不变，只有配置能力，没有运行期执行。</li>
 * <li>支付尝试（PAYMENT_ATTEMPT_RISK）只接受统一变量格式，规则可冻结并经执行发布运行；一期不开放附加动作；认证要求是附加输出，由放行结束节点配置。</li>
 */
public final class DecisionPointAuthoringDescriptor {

    private static final List<ComparisonOperator> OPERATORS =
        List.of(ComparisonOperator.EQ, ComparisonOperator.NE, ComparisonOperator.GT, ComparisonOperator.GTE,
            ComparisonOperator.LT, ComparisonOperator.LTE, ComparisonOperator.IN, ComparisonOperator.NOT_IN,
            ComparisonOperator.BETWEEN, ComparisonOperator.IS_NULL, ComparisonOperator.IS_NOT_NULL);

    /** 支付决策点的认证要求输出编码。 */
    public static final String AUTHENTICATION_REQUIREMENT = "AUTHENTICATION_REQUIREMENT";

    private static final DecisionPointAuthoringDescriptor CHECKOUT_PREPARE =
        new DecisionPointAuthoringDescriptor(
            "CHECKOUT_PREPARE_AUTHORING_V1", new DecisionPointDescriptor("CHECKOUT_PREPARE", "Checkout Prepare",
                "CHECKOUT", "ORDER_PREPARATION", 4, "RISK_DECISION_RESULT", DecisionValue.REVIEW,
                EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500, ConfigurationStatus.ACTIVE),
            240001L,
            List.of(
                action("ALLOW_CHECKOUT", "Allow Checkout", "DECISION_ALLOW_V1", DecisionValue.ALLOW,
                    "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1"),
                action("REVIEW_CHECKOUT", "Review Checkout", "DECISION_REVIEW_V1", DecisionValue.REVIEW,
                    "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716"),
                action("DENY_CHECKOUT", "Deny Checkout", "DECISION_DENY_V1", DecisionValue.DENY,
                    "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7")),
            List.of(), false);

    private static final DecisionPointAuthoringDescriptor PAYMENT_ATTEMPT_RISK =
        new DecisionPointAuthoringDescriptor(
            "PAYMENT_ATTEMPT_RISK_AUTHORING_V1", new DecisionPointDescriptor("PAYMENT_ATTEMPT_RISK",
                "Payment Attempt Risk", "PAYMENT", "PAYMENT_ATTEMPT", 1, "RISK_DECISION_RESULT", DecisionValue.ALLOW,
                EvaluationStrategy.FIRST_TERMINAL, FailureStrategy.FAIL_CLOSED, 500, ConfigurationStatus.ACTIVE),
            240006L,
            List.of(
                action("ALLOW_PAYMENT", "放行支付", "DECISION_ALLOW_V1", DecisionValue.ALLOW,
                    "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1"),
                action("REVIEW_PAYMENT", "人工审核", "DECISION_REVIEW_V1", DecisionValue.REVIEW,
                    "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716"),
                action("DENY_PAYMENT", "拒绝支付", "DECISION_DENY_V1", DecisionValue.DENY,
                    "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7")),
            List.of(new OutputDescriptor(AUTHENTICATION_REQUIREMENT, "认证要求",
                List.of(new OutputValue(AuthenticationRequirement.NOT_REQUIRED.getCode(), "无需认证"),
                    new OutputValue(AuthenticationRequirement.THREE_DS_REQUIRED.getCode(), "需要 3DS")),
                AuthenticationRequirement.NOT_REQUIRED.getCode())),
            true);

    private static final List<DecisionPointAuthoringDescriptor> ALL = List.of(CHECKOUT_PREPARE, PAYMENT_ATTEMPT_RISK);

    private final String descriptorCode;
    private final DecisionPointDescriptor point;
    private final long firstActionVersionId;
    private final List<InputFieldDescriptor> fields;
    private final List<DecisionActionDescriptor> actions;
    private final List<OutputDescriptor> outputs;
    private final boolean runtimeSupported;

    /** 由工程合同构造；输入字段取自同一决策点的准确输入合同。 */
    private DecisionPointAuthoringDescriptor(String descriptorCode, DecisionPointDescriptor point,
        long firstActionVersionId, List<DecisionActionDescriptor> actions, List<OutputDescriptor> outputs,
        boolean runtimeSupported) {
        this.descriptorCode = descriptorCode;
        this.point = point;
        this.firstActionVersionId = firstActionVersionId;
        this.actions = List.copyOf(actions);
        this.outputs = List.copyOf(outputs);
        this.runtimeSupported = runtimeSupported;
        this.fields = DecisionSourceContracts.all().stream()
            .filter(c -> c.sourceType() == VariableSource.INPUT
                && c.pointCode().equals(point.pointCode()) && c.contractCode().equals(point.inputContractCode())
                && c.contractVersion() == point.inputContractVersion()).findFirst().orElseThrow().fields().stream()
            .map(f -> new InputFieldDescriptor(f.fieldKey(), f.valueType(), f.required(), f.dataClassification(),
                f.allowedUsages().contains(VariableUsage.RULE_CONDITION),
                f.allowedUsages().contains(VariableUsage.ACTION_PARAMETER))).toList();
    }

    /** 结账准备决策点的原合同。 */
    public static DecisionPointAuthoringDescriptor checkoutPrepare() {
        return CHECKOUT_PREPARE;
    }

    /** 支付尝试风控决策点合同。 */
    public static DecisionPointAuthoringDescriptor paymentAttemptRisk() {
        return PAYMENT_ATTEMPT_RISK;
    }

    /** 全部已注册决策点，按注册顺序。 */
    public static List<DecisionPointAuthoringDescriptor> all() {
        return ALL;
    }

    /** 按决策点编码取准确合同；未注册时返回空，由调用方决定错误语义。 */
    public static Optional<DecisionPointAuthoringDescriptor> find(String pointCode) {
        return ALL.stream().filter(value -> value.point.pointCode().equals(pointCode)).findFirst();
    }

    /** 按决策点编码取准确合同；未注册时视为参数错误。 */
    public static DecisionPointAuthoringDescriptor require(String pointCode) {
        return find(pointCode).orElseThrow(
            () -> new InvalidParameterException("决策点尚未支持: " + pointCode));
    }

    /** 取拥有该终态动作的决策点合同；动作未注册属于工程错误。 */
    public static DecisionPointAuthoringDescriptor ownerOf(DecisionActionDescriptor action) {
        return ALL.stream().filter(value -> value.actions.contains(action)).findFirst()
            .orElseThrow(() -> new IllegalStateException("终态动作未归属任何决策点: " + action.actionCode()));
    }

    public String descriptorCode() {
        return descriptorCode;
    }

    /** 首个终态动作版本 ID；其余终态动作按注册顺序连续递增。 */
    public long firstActionVersionId() {
        return firstActionVersionId;
    }

    /** 该决策点规则是否经执行发布在运行期执行；为 false 时规则只允许配置。 */
    public boolean runtimeSupported() {
        return runtimeSupported;
    }

    /** 该决策点是否开放冷却、打标签等附加动作配置。 */
    public boolean additionalActionsSupported() {
        return !runtimeSupported;
    }

    /** 该决策点声明的附加输出，按注册顺序；由放行结束节点配置取值。 */
    public List<OutputDescriptor> outputs() {
        return outputs;
    }

    /** 按输出编码取声明；未声明时返回空。 */
    public Optional<OutputDescriptor> output(String outputCode) {
        return outputs.stream().filter(value -> value.outputCode().equals(outputCode)).findFirst();
    }

    /** 终态动作是否属于本决策点。 */
    public boolean ownsAction(String actionCode, int versionNo) {
        return actions.stream()
            .anyMatch(value -> value.actionCode().equals(actionCode) && value.versionNo() == versionNo);
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
            throw new ErrorException("规则输入合同未登记");
        }
        return fields;
    }

    /** 当前创作合同的完整输入目录，包含仅动作可用字段。 */
    public List<InputFieldDescriptor> inputFields() {
        return inputFields(point.pointCode(), point.inputContractCode(), point.inputContractVersion());
    }

    public List<ComparisonOperator> operators() {
        return OPERATORS;
    }

    public List<DecisionActionDescriptor> actions() {
        return actions;
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

    /**
     * 风控点附加输出声明；只能由放行结束节点配置
     *
     * @param outputCode
     *            输出编码
     * @param name
     *            展示名称
     * @param values
     *            允许取值及展示名称，按展示顺序
     * @param defaultValue
     *            放行结束节点未配置时的取值，保存流程时写入
     */
    public record OutputDescriptor(String outputCode, String name, List<OutputValue> values, String defaultValue) {
        /** 可配置附加输出的结束决定。 */
        public static final DecisionValue END_DECISION = DecisionValue.ALLOW;

        /** 校验默认值属于允许取值。 */
        public OutputDescriptor {
            Objects.requireNonNull(outputCode);
            Objects.requireNonNull(name);
            values = List.copyOf(values);
            if (values.stream().noneMatch(value -> value.value().equals(defaultValue))) {
                throw new IllegalArgumentException("默认值必须在允许取值内: " + outputCode);
            }
        }

        /** 允许的取值编码，按展示顺序。 */
        public List<String> allowedValues() {
            return values.stream().map(OutputValue::value).toList();
        }
    }

    /**
     * 附加输出的一个取值
     *
     * @param value
     *            取值编码
     * @param name
     *            展示名称
     */
    public record OutputValue(String value, String name) {
        /** 编码与名称必填。 */
        public OutputValue {
            Objects.requireNonNull(value);
            Objects.requireNonNull(name);
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
