package com.paytech.risk.decision.domain.authoring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.control.domain.list.RiskSubjectType;
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
 * <li>结账（CHECKOUT_PREPARE）当前合同为 ORDER_PREPARATION v5，只接受统一变量格式，规则可冻结并经执行发布运行；不开放附加动作；开放以 Client、运营主体为主体的实时累计与名单命中变量；v4 只供已有规则集与流程读取。</li>
 * <li>结账旧合同（v4，{@link #legacyCheckoutPrepare()}）只服务旧格式规则、旧候选发布与流程 schemaVersion 1 的解释，不进入 {@link #all()}。</li>
 * <li>支付尝试（PAYMENT_ATTEMPT_RISK）只接受统一变量格式，规则可冻结并经执行发布运行；一期不开放附加动作；认证要求是附加输出，由放行结束节点配置。</li>
 */
public final class DecisionPointAuthoringDescriptor {

    private static final List<ComparisonOperator> OPERATORS =
        List.of(ComparisonOperator.EQ, ComparisonOperator.NE, ComparisonOperator.GT, ComparisonOperator.GTE,
            ComparisonOperator.LT, ComparisonOperator.LTE, ComparisonOperator.IN, ComparisonOperator.NOT_IN,
            ComparisonOperator.BETWEEN, ComparisonOperator.IS_NULL, ComparisonOperator.IS_NOT_NULL);

    /** 支付决策点的认证要求输出编码。 */
    public static final String AUTHENTICATION_REQUIREMENT = "AUTHENTICATION_REQUIREMENT";

    /** 结账终态动作；当前合同与旧合同共用同一组动作版本。 */
    private static final List<DecisionActionDescriptor> CHECKOUT_ACTIONS = List.of(
        action("ALLOW_CHECKOUT", "Allow Checkout", "DECISION_ALLOW_V1", DecisionValue.ALLOW,
            "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1"),
        action("REVIEW_CHECKOUT", "Review Checkout", "DECISION_REVIEW_V1", DecisionValue.REVIEW,
            "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716"),
        action("DENY_CHECKOUT", "Deny Checkout", "DECISION_DENY_V1", DecisionValue.DENY,
            "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7"));

    /** 结账累计事件写入的主体，也是结账累计与名单命中可选的主体：结账输入只提供 Client 与运营主体。 */
    private static final List<RiskSubjectType> CHECKOUT_SUBJECTS =
        List.of(RiskSubjectType.CLIENT, RiskSubjectType.OPERATING_ENTITY);

    /** 支付累计事件写入的主体；卡 BIN 只用于名单。 */
    private static final List<RiskSubjectType> PAYMENT_VELOCITY_SUBJECTS = List.of(RiskSubjectType.CLIENT,
        RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS,
        RiskSubjectType.CARD_FINGERPRINT);

    /** 支付名单命中可选的主体。 */
    private static final List<RiskSubjectType> PAYMENT_LIST_SUBJECTS = List.of(RiskSubjectType.CLIENT,
        RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS,
        RiskSubjectType.CARD_FINGERPRINT, RiskSubjectType.CARD_BIN);

    /** 结账运行期点版本号；mor-test 已有旧 companion 点版本 1～33，取各环境都未使用的固定值。 */
    static final int CHECKOUT_RUNTIME_POINT_VERSION_NO = 100;

    private static final DecisionPointAuthoringDescriptor CHECKOUT_PREPARE =
        new DecisionPointAuthoringDescriptor(
            "CHECKOUT_PREPARE_AUTHORING_V2", checkoutPoint(5), 240001L, CHECKOUT_ACTIONS, List.of(),
            new RuntimeCapabilities(true, true, CHECKOUT_RUNTIME_POINT_VERSION_NO, CHECKOUT_SUBJECTS,
                CHECKOUT_SUBJECTS, "INPUT_CURRENCY"), List.of(5, 4));

    private static final DecisionPointAuthoringDescriptor LEGACY_CHECKOUT_PREPARE =
        new DecisionPointAuthoringDescriptor(
            "CHECKOUT_PREPARE_AUTHORING_V1", checkoutPoint(4), 240001L, CHECKOUT_ACTIONS, List.of(),
            new RuntimeCapabilities(false, false, 0, List.of(), List.of(), null), List.of(4));

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
            new RuntimeCapabilities(true, true, 1, PAYMENT_VELOCITY_SUBJECTS,
                PAYMENT_LIST_SUBJECTS, "PAY_INPUT_CURRENCY"), List.of(1));

    private static final List<DecisionPointAuthoringDescriptor> ALL = List.of(CHECKOUT_PREPARE, PAYMENT_ATTEMPT_RISK);

    private final String descriptorCode;
    private final DecisionPointDescriptor point;
    private final long firstActionVersionId;
    private final List<InputFieldDescriptor> fields;
    private final List<DecisionActionDescriptor> actions;
    private final List<OutputDescriptor> outputs;
    private final RuntimeCapabilities runtime;
    private final Map<Integer, List<InputFieldDescriptor>> readableFields;

    /**
     * 由工程合同构造；输入字段取自同一决策点的准确输入合同
     *
     * @param readableVersions 可读取的输入合同版本，首个为当前合同
     */
    private DecisionPointAuthoringDescriptor(String descriptorCode, DecisionPointDescriptor point,
        long firstActionVersionId, List<DecisionActionDescriptor> actions, List<OutputDescriptor> outputs,
        RuntimeCapabilities runtime, List<Integer> readableVersions) {
        if (readableVersions.getFirst() != point.inputContractVersion()) {
            throw new IllegalStateException("可读合同的首个版本必须是当前合同: " + point.pointCode());
        }
        this.descriptorCode = descriptorCode;
        this.point = point;
        this.firstActionVersionId = firstActionVersionId;
        this.actions = List.copyOf(actions);
        this.outputs = List.copyOf(outputs);
        this.runtime = runtime;
        Map<Integer, List<InputFieldDescriptor>> fieldsByVersion = new LinkedHashMap<>();
        readableVersions.forEach(version -> fieldsByVersion.put(version, contractFields(point, version)));
        this.readableFields = Collections.unmodifiableMap(fieldsByVersion);
        this.fields = readableFields.get(point.inputContractVersion());
    }

    /** 同一决策点某一版本输入合同的字段目录。 */
    private static List<InputFieldDescriptor> contractFields(DecisionPointDescriptor point, int contractVersion) {
        return DecisionSourceContracts.all().stream()
            .filter(c -> c.sourceType() == VariableSource.INPUT
                && c.pointCode().equals(point.pointCode()) && c.contractCode().equals(point.inputContractCode())
                && c.contractVersion() == contractVersion).findFirst().orElseThrow().fields().stream()
            .map(f -> new InputFieldDescriptor(f.fieldKey(), f.valueType(), f.required(), f.dataClassification(),
                f.allowedUsages().contains(VariableUsage.RULE_CONDITION),
                f.allowedUsages().contains(VariableUsage.ACTION_PARAMETER))).toList();
    }

    /** 结账决策点在指定输入合同版本下的点合同；其余属性两个版本相同。 */
    private static DecisionPointDescriptor checkoutPoint(int contractVersion) {
        return new DecisionPointDescriptor("CHECKOUT_PREPARE", "Checkout Prepare", "CHECKOUT", "ORDER_PREPARATION",
            contractVersion, "RISK_DECISION_RESULT", DecisionValue.REVIEW, EvaluationStrategy.FIRST_TERMINAL,
            FailureStrategy.FAIL_CLOSED, 500, ConfigurationStatus.ACTIVE);
    }

    /** 结账准备决策点的当前合同（ORDER_PREPARATION v5，运行期）。 */
    public static DecisionPointAuthoringDescriptor checkoutPrepare() {
        return CHECKOUT_PREPARE;
    }

    /**
     * 结账准备决策点的旧合同（ORDER_PREPARATION v4，仅配置）
     * <li>只供旧格式规则、旧候选发布与流程 schemaVersion 1 的解释使用，行为与本需求前的结账合同完全相同；不进入 {@link #all()}。</li>
     */
    public static DecisionPointAuthoringDescriptor legacyCheckoutPrepare() {
        return LEGACY_CHECKOUT_PREPARE;
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
        return runtime.supported();
    }

    /** 该决策点是否开放冷却、打标签等附加动作配置。 */
    public boolean additionalActionsSupported() {
        return !runtime.supported();
    }

    /** 该决策点是否允许实时累计与名单命中变量；只有运行期计算这两类 Feature 的决策点为 true。 */
    public boolean featureVariablesSupported() {
        return runtime.featureVariablesSupported();
    }

    /** 累计事件写入的主体，也是可锚定与可去重的主体，按登记顺序；不支持 Feature 变量时为空。 */
    public List<RiskSubjectType> velocitySubjects() {
        return runtime.velocitySubjects();
    }

    /** 名单命中可选的主体，按登记顺序；不支持 Feature 变量时为空。 */
    public List<RiskSubjectType> listSubjects() {
        return runtime.listSubjects();
    }

    /** 金额类累计变量绑定的本点币种输入变量（系统变量）；不支持 Feature 变量时为空。 */
    public String currencyVariableCode() {
        return runtime.currencyVariableCode();
    }

    /** 执行发布引用的冻结决策点版本号；不支持运行期的决策点没有该版本。 */
    public int runtimePointVersionNo() {
        if (!runtime.supported()) {
            throw new IllegalStateException("决策点不支持运行期: " + point.pointCode());
        }
        return runtime.pointVersionNo();
    }

    /** 该决策点是否可读取指定输入合同；当前合同与登记的旧合同都可读取。 */
    public boolean readsContract(String contractCode, int contractVersion) {
        return point.inputContractCode().equals(contractCode) && readableFields.containsKey(contractVersion);
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

    /** 按准确输入合同解释历史和新配置，不使用最新版本兜底；旧合同返回其原字段目录。 */
    public List<InputFieldDescriptor> inputFields(String pointCode, String contractCode, int contractVersion) {
        if (!point.pointCode().equals(pointCode) || !readsContract(contractCode, contractVersion)) {
            throw new ErrorException("规则输入合同未登记");
        }
        return readableFields.get(contractVersion);
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

    /**
     * 决策点运行期能力
     *
     * @param supported 规则是否经执行发布在运行期执行
     * @param featureVariablesSupported 是否允许实时累计与名单命中变量
     * @param pointVersionNo 执行发布引用的冻结决策点版本号；不支持运行期时为 0
     * @param velocitySubjects 累计事件写入、可锚定与可去重的主体
     * @param listSubjects 名单命中可选的主体
     * @param currencyVariableCode 金额类累计变量绑定的币种输入变量
     *         <li>三项只描述 Feature 能力，不进入点版本投影与摘要。</li>
     */
    private record RuntimeCapabilities(boolean supported, boolean featureVariablesSupported, int pointVersionNo,
        List<RiskSubjectType> velocitySubjects, List<RiskSubjectType> listSubjects, String currencyVariableCode) { }

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
