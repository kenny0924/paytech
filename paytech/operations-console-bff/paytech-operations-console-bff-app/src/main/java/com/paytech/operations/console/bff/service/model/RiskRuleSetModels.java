package com.paytech.operations.console.bff.service.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Operations 自有 Rule Set 浏览器合同；规则语义及上游状态校验由各自 Owner 处理。 */
public final class RiskRuleSetModels {
    static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";
    static final String FIELD_CODE = "^[a-z][a-z0-9_]{0,63}$";
    static final String NODE_KEY = "^[\\x21-\\x7E]{1,64}$";
    static final String POSITIVE_DECIMAL_ID = "^[1-9][0-9]{0,18}$";
    static final String CONTENT_HASH = "^[0-9a-f]{64}$";

    private RiskRuleSetModels() {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleSetQueryRequestBody(
        @Pattern(regexp = CODE) String pointCode,
        @Pattern(regexp = CODE) String ruleSetCode,
        @Pattern(regexp = "^(DRAFT|FROZEN|RETIRED)$") String status) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CreateRuleSetVersionRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE) String pointCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE) String ruleSetCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 255) String ruleSetName,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = POSITIVE_DECIMAL_ID) String baseRuleSetVersionId,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = CONTENT_HASH) String baseContentHash,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 500) String changeReason,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 200)
        List<@NotNull @Valid RuleRequestBody> rules,
        /** 新保存必须显式指定，空值仅用于原成功命令回放。 */
        @Pattern(regexp = "^VARIABLE_REFERENCE$") String operandFormat) {
        public CreateRuleSetVersionRequestBody(String pointCode, String ruleSetCode, String ruleSetName,
            String baseRuleSetVersionId, String baseContentHash, String changeReason, List<RuleRequestBody> rules) {
            this(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules, null);
        }

        public CreateRuleSetVersionRequestBody {
            rules = requestSnapshot(rules);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE) String ruleCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 255) String ruleName,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer priorityNo,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull Boolean enabledFlag,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Size(max = 64) @Pattern(regexp = CODE) String reasonCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 2000)
        List<@NotNull @Valid ConditionNodeRequestBody> conditionNodes,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Valid RuleActionRequestBody action,
        @Size(max = 500) String reasonMessage,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Size(max = 16)
        List<@NotNull @Valid AdditionalAction> additionalActions) {

        public RuleRequestBody(String ruleCode, String ruleName, Integer priorityNo, Boolean enabledFlag,
            String reasonCode, List<ConditionNodeRequestBody> conditionNodes, RuleActionRequestBody action) {
            this(ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, conditionNodes, action, null, List.of());
        }
        public RuleRequestBody {
            additionalActions = requestSnapshot(additionalActions);
            conditionNodes = requestSnapshot(conditionNodes);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConditionNodeRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = NODE_KEY) String nodeKey,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = NODE_KEY) String parentNodeKey,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer sequenceNo,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(GROUP|PREDICATE)$") String nodeType,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = "^(AND|OR|NOT)$") String groupOperator,
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @Pattern(regexp = "^(EQ|NE|GT|GTE|LT|LTE|IN|NOT_IN|BETWEEN|IS_NULL|IS_NOT_NULL)$") String comparisonOperator,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(max = 101)
        List<@NotNull @Valid OperandRequestBody> operands) {

        public ConditionNodeRequestBody {
            operands = requestSnapshot(operands);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OperandRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(LEFT|RIGHT)$") String operandRole,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer valueSequence,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(CONTEXT|VARIABLE|LITERAL)$") String sourceType,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Size(max = 64) @Pattern(regexp = FIELD_CODE) String fieldCode,
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank @Pattern(regexp = "^(STRING|NUMBER|BOOLEAN|DATETIME|DURATION)$") String valueType,
        @JsonProperty(isRequired = OptBoolean.TRUE) Object literalValue,
        @Pattern(regexp = CODE) String variableCode,
        @Pattern(regexp = "^(SCALAR|MONEY)$") String numericUnit,
        @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
        @Pattern(regexp = CODE) String currencyVariableCode) {
        public OperandRequestBody(String operandRole, Integer valueSequence, String sourceType, String fieldCode,
            String valueType, Object literalValue) {
            this(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue, null, null, null, null);
        }

        public OperandRequestBody {
            if (literalValue != null && !(literalValue instanceof String) && !(literalValue instanceof Boolean)) {
                throw new IllegalArgumentException("literalValue 必须是 String、Boolean 或 null");
            }
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleActionRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE) String actionCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer actionVersionNo) {
    }

    /** 非终态动作仅保存配置，不执行真实副作用。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdditionalAction(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer sequenceNo,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE) String actionCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer actionVersionNo,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE) String targetBindingCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 32)
        List<@NotNull @Valid ActionParameter> parameters,
        /** 新动作主体指向库中真实 INPUT；原成功回放可空。 */
        @Pattern(regexp = CODE) String subjectVariableCode) {
        public AdditionalAction(Integer sequenceNo, String actionCode, Integer actionVersionNo,
            String targetBindingCode, List<ActionParameter> parameters) {
            this(sequenceNo, actionCode, actionVersionNo, targetBindingCode, parameters, null);
        }
        public AdditionalAction { parameters = requestSnapshot(parameters); }
    }

    /** 参数为类型化 Literal 或批准的 Context 引用；实际目标参数由 Risk 生成。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ActionParameter(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE) String parameterCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive Integer valueSequence,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(CONTEXT|LITERAL)$") String sourceType,
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = FIELD_CODE) String fieldCode,
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank
        @Pattern(regexp = "^(STRING|NUMBER|BOOLEAN|DATETIME|DURATION)$") String valueType,
        @JsonProperty(isRequired = OptBoolean.TRUE) Object literalValue) {
        public ActionParameter {
            if (literalValue != null && !(literalValue instanceof String) && !(literalValue instanceof Boolean))
                throw new IllegalArgumentException("literalValue 必须是 String、Boolean 或 null");
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FreezeRuleSetVersionRequestBody(
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Pattern(regexp = CONTENT_HASH) String expectedContentHash) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleAuthoringContextResponseBody(
        String descriptorCode,
        DecisionPointDescriptorResponseBody point,
        List<DecisionInputFieldDescriptorResponseBody> fields,
        List<String> operators,
        List<DecisionActionDescriptorResponseBody> actions,
        List<Void> features,
        List<ActionParameterDescriptor> actionParameters,
        List<Void> obligations,
        List<Void> limitBindings,
        List<TargetBindingDescriptor> targetBindings,
        String operandFormat,
        RiskVariableModels.VariableQueryContext variableQueryContext) {
        public RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point,
            List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
            List<DecisionActionDescriptorResponseBody> actions, List<Void> features,
            List<ActionParameterDescriptor> actionParameters, List<Void> obligations, List<Void> limitBindings,
            List<TargetBindingDescriptor> targetBindings) {
            this(descriptorCode, point, fields, operators, actions, features, actionParameters, obligations,
                limitBindings, targetBindings, null, null);
        }

        public RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point,
            List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
            List<DecisionActionDescriptorResponseBody> actions, List<Void> features,
            List<ActionParameterDescriptor> actionParameters, List<Void> obligations, List<Void> limitBindings) {
            this(descriptorCode, point, fields, operators, actions, features, actionParameters, obligations,
                limitBindings, List.of());
        }

        public RuleAuthoringContextResponseBody {
            targetBindings = List.copyOf(targetBindings);
            fields = List.copyOf(fields);
            operators = List.copyOf(operators);
            actions = List.copyOf(actions);
            features = List.copyOf(features);
            actionParameters = List.copyOf(actionParameters);
            obligations = List.copyOf(obligations);
            limitBindings = List.copyOf(limitBindings);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DecisionPointDescriptorResponseBody(
        String pointCode, String pointName, String businessDomain, String inputContractCode,
        int inputContractVersion, String outputContractCode, String defaultDecision,
        String evaluationStrategy, String failureStrategy, int timeoutMillis, String status) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DecisionInputFieldDescriptorResponseBody(
        String fieldCode, String valueType, boolean requiredFlag, String dataClassification,
        boolean ruleReferenceFlag, boolean actionParameterFlag, String variableCode) {
        public DecisionInputFieldDescriptorResponseBody(String fieldCode, String valueType, boolean requiredFlag,
            String dataClassification, boolean ruleReferenceFlag, boolean actionParameterFlag) {
            this(fieldCode, valueType, requiredFlag, dataClassification, ruleReferenceFlag, actionParameterFlag, null);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DecisionActionDescriptorResponseBody(
        String actionCode, String actionName, String actionCategory, int versionNo, boolean terminalFlag,
        String decisionCode, String mergePolicy, boolean shadowSupportedFlag, String status) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleSetListItemResponseBody(
        String ruleSetVersionId,
        /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
        String pointVersionId, String pointCode, String ruleSetCode, String ruleSetName,
        int versionNo, String status, String contentHash, String createdBy, String changeReason, String createTime,
        String frozenBy, String frozenTime,
        /** 整体规则集身份，供删除与列表行键使用。 */
        String ruleSetId,
        /** 当前根管理 CAS 版本的正十进制文本。 */
        String managementVersion,
        /** 是否有任一版本被当前活动发布使用。 */
        boolean inUse) implements RuleSetVersionFields {
    }

    /** 删除确认绑定最近读取的管理版本。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeleteRuleSetRequestBody(
        @NotBlank @Pattern(regexp = POSITIVE_DECIMAL_ID) @DecimalMax("9223372036854775807") String expectedVersion) { }

    /** Owner 的不可变删除回执，BFF 不产生删除事实。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeleteRuleSetResponseBody(String ruleSetId, String status, String version,
        String deletedBy, String deletedTime) { }

    /** 列表及详情共享的版本内容校验面；根元数据仅属于列表。 */
    public interface RuleSetVersionFields {
        /** 版本身份。 */
        String ruleSetVersionId();
        /** 伴随的决策点版本。 */
        String pointVersionId();
        /** 所属决策点编码。 */
        String pointCode();
        /** 决策点内的规则集编码。 */
        String ruleSetCode();
        /** 配置名称。 */
        String ruleSetName();
        /** 不可变配置的业务版本号。 */
        int versionNo();
        /** 版本状态，与管理删除状态独立。 */
        String status();
        /** 不可变内容摘要。 */
        String contentHash();
        /** 保存操作人。 */
        String createdBy();
        /** 保存的变更原因。 */
        String changeReason();
        /** 配置创建 UTC 微秒时间。 */
        String createTime();
        /** 冻结人，未冻结时为空。 */
        String frozenBy();
        /** 冻结时间，未冻结时为空。 */
        String frozenTime();
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RuleSetDetailResponseBody(
        String ruleSetVersionId,
        /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
        String pointVersionId, String pointCode, String ruleSetCode, String ruleSetName,
        int versionNo, String status, String contentHash, String createdBy, String changeReason, String createTime,
        String frozenBy, String frozenTime, List<RuleRequestBody> rules,
        String operandFormat,
        List<RiskVariableModels.VariableSummary> currentVariables,
        String valuesAsOf,
        List<RiskVariableModels.VariableIssue> variableIssues) implements RuleSetVersionFields {
        public RuleSetDetailResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode,
            String ruleSetCode, String ruleSetName, int versionNo, String status, String contentHash,
            String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime, List<RuleRequestBody> rules) {
            this(ruleSetVersionId, pointVersionId, pointCode, ruleSetCode, ruleSetName, versionNo, status, contentHash,
                createdBy, changeReason, createTime, frozenBy, frozenTime, rules, null, List.of(), null, List.of());
        }

        public RuleSetDetailResponseBody {
            rules = List.copyOf(rules);
            currentVariables = List.copyOf(currentVariables);
            variableIssues = List.copyOf(variableIssues);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CreateRuleSetVersionResponseBody(
        String ruleSetVersionId,
        /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
        String pointVersionId, String ruleSetCode, int versionNo, String status,
        String contentHash, String createdBy, String createTime) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FreezeRuleSetVersionResponseBody(
        String ruleSetVersionId,
        /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
        String pointVersionId, String ruleSetCode, int versionNo, String status,
        String contentHash, String frozenBy, String frozenTime) {
    }

    /** 当前 Point 批准的完整主体绑定，包含命名空间等必要组成部分。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TargetBindingDescriptor(String bindingCode, String displayName, String subjectType,
                                          List<TargetFieldDescriptor> requiredFields, String subjectSemanticRole) {
        public TargetBindingDescriptor(String bindingCode, String displayName, String subjectType, List<TargetFieldDescriptor> requiredFields) {
            this(bindingCode, displayName, subjectType, requiredFields, null);
        }
        public TargetBindingDescriptor { requiredFields = List.copyOf(requiredFields); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TargetFieldDescriptor(String role, String fieldCode) { }

    /** 参数值域由 Risk 定义；时长上下限为毫秒字符串。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ActionParameterDescriptor(String actionCode, Integer actionVersionNo, String parameterCode,
        String valueType, Boolean requiredFlag, String allowedSourceType, Boolean targetParameterFlag,
        Integer maxValues, String minimumValue, String maximumValue, String valuePattern) { }

    private static <T> List<T> requestSnapshot(List<T> values) {
        // 保留 null 集合与元素，交给 Jakarta 产生字段级 422，避免构造异常变成 JSON 400。
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
