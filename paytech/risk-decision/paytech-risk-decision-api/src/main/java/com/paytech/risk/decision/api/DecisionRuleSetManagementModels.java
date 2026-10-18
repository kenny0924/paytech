package com.paytech.risk.decision.api;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Risk Rule Set 管理 HTTP 模型。 */
public final class DecisionRuleSetManagementModels {

    public static final String CODE = "^[A-Z][A-Z0-9_]{0,63}$";
    public static final String POSITIVE_DECIMAL_ID = "^[1-9][0-9]{0,18}$";
    public static final String CONTENT_HASH = "^[0-9a-f]{64}$";
    public static final String FIELD_CODE = "^[a-z][a-z0-9_]{0,63}$";
    public static final String NODE_KEY = "^[\\x21-\\x7E]{1,64}$";

    /** 共享类型容器不允许实例化。 */
    private DecisionRuleSetManagementModels() {
    }

    /** 完整规则配置，详情沿用同一 JSON 结构。 */
    public static class RuleRequestBody {

        /** 规则编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE)
        private String ruleCode;

        /** 规则名称。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 255)
        private String ruleName;

        /** 规则优先级。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer priorityNo;

        /** 是否启用。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull
        private Boolean enabledFlag;

        /** 内部拒绝原因代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Size(max = 64) @Pattern(regexp = CODE)
        private String reasonCode;

        /** 条件树节点。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 2000)
        private List<@NotNull @Valid ConditionNodeRequestBody> conditionNodes;

        /** 可选的终态决定动作。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Valid
        private RuleActionRequestBody action;

        /** 拒绝规则的内部说明，随版本冻结；旧记录可空。 */
        @Size(max = 500)
        private String reasonMessage;

        /** 历史详情允许缺失；新配置必须传数组，空数组表示没有附加动作。 */
        @JsonInclude(JsonInclude.Include.NON_NULL) @Size(max = 16)
        private List<@NotNull @Valid AdditionalAction> additionalActions;

        /** JSON 反序列化构造。 */
        public RuleRequestBody() { }

        /** 按完整合同构造。 */
        public RuleRequestBody(String ruleCode, String ruleName, Integer priorityNo, Boolean enabledFlag, String reasonCode, List<ConditionNodeRequestBody> conditionNodes, RuleActionRequestBody action, String reasonMessage, List<AdditionalAction> additionalActions) {
            setRuleCode(ruleCode);
            setRuleName(ruleName);
            setPriorityNo(priorityNo);
            setEnabledFlag(enabledFlag);
            setReasonCode(reasonCode);
            setConditionNodes(conditionNodes);
            setAction(action);
            setReasonMessage(reasonMessage);
            setAdditionalActions(additionalActions);
        }

        /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
        public RuleRequestBody(String ruleCode, String ruleName, Integer priorityNo, Boolean enabledFlag,
            String reasonCode, List<ConditionNodeRequestBody> conditionNodes, RuleActionRequestBody action) {
            this(ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, conditionNodes, action, null, null);
        }

        /** 返回规则编码。 */
        public String getRuleCode() { return ruleCode; }

        /** 设置规则编码。 */
        public void setRuleCode(String ruleCode) {
            this.ruleCode = ruleCode;
        }

        /** 返回规则名称。 */
        public String getRuleName() { return ruleName; }

        /** 设置规则名称。 */
        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        /** 返回规则优先级。 */
        public Integer getPriorityNo() { return priorityNo; }

        /** 设置规则优先级。 */
        public void setPriorityNo(Integer priorityNo) {
            this.priorityNo = priorityNo;
        }

        /** 返回是否启用。 */
        public Boolean getEnabledFlag() { return enabledFlag; }

        /** 设置是否启用。 */
        public void setEnabledFlag(Boolean enabledFlag) {
            this.enabledFlag = enabledFlag;
        }

        /** 返回内部拒绝原因代码。 */
        public String getReasonCode() { return reasonCode; }

        /** 设置内部拒绝原因代码。 */
        public void setReasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        /** 返回条件树节点。 */
        public List<ConditionNodeRequestBody> getConditionNodes() { return conditionNodes; }

        /** 设置条件树节点。 */
        public void setConditionNodes(List<ConditionNodeRequestBody> conditionNodes) {
            this.conditionNodes = conditionNodes == null ? null : List.copyOf(conditionNodes);
        }

        /** 返回可选的终态决定动作。 */
        public RuleActionRequestBody getAction() { return action; }

        /** 设置可选的终态决定动作。 */
        public void setAction(RuleActionRequestBody action) {
            this.action = action;
        }

        /** 返回拒绝规则的内部说明，随版本冻结；旧记录可空。 */
        public String getReasonMessage() { return reasonMessage; }

        /** 设置拒绝规则的内部说明，随版本冻结；旧记录可空。 */
        public void setReasonMessage(String reasonMessage) {
            this.reasonMessage = reasonMessage;
        }

        /** 返回历史详情允许缺失；新配置必须传数组，空数组表示没有附加动作。 */
        public List<AdditionalAction> getAdditionalActions() { return additionalActions; }

        /** 设置历史详情允许缺失；新配置必须传数组，空数组表示没有附加动作。 */
        public void setAdditionalActions(List<AdditionalAction> additionalActions) {
            this.additionalActions = additionalActions == null ? null : List.copyOf(additionalActions);
        }
    }

    /** 条件树节点与类型化操作数。 */
    public static class ConditionNodeRequestBody {

        /** 节点在当前规则中的键。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = NODE_KEY)
        private String nodeKey;

        /** 父节点键；根节点为 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = NODE_KEY)
        private String parentNodeKey;

        /** 同级顺序。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer sequenceNo;

        /** 条件节点类型。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(GROUP|PREDICATE)$")
        private String nodeType;

        /** 分组运算符。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = "^(AND|OR|NOT)$")
        private String groupOperator;

        /** 比较运算符。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @Pattern(regexp = "^(EQ|NE|GT|GTE|LT|LTE|IN|NOT_IN|BETWEEN|IS_NULL|IS_NOT_NULL)$")
        private String comparisonOperator;

        /** 类型化操作数。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(max = 101)
        private List<@NotNull @Valid OperandRequestBody> operands;

        /** JSON 反序列化构造。 */
        public ConditionNodeRequestBody() { }

        /** 按完整合同构造。 */
        public ConditionNodeRequestBody(String nodeKey, String parentNodeKey, Integer sequenceNo, String nodeType, String groupOperator, String comparisonOperator, List<OperandRequestBody> operands) {
            setNodeKey(nodeKey);
            setParentNodeKey(parentNodeKey);
            setSequenceNo(sequenceNo);
            setNodeType(nodeType);
            setGroupOperator(groupOperator);
            setComparisonOperator(comparisonOperator);
            setOperands(operands);
        }

        /** 返回节点在当前规则中的键。 */
        public String getNodeKey() { return nodeKey; }

        /** 设置节点在当前规则中的键。 */
        public void setNodeKey(String nodeKey) {
            this.nodeKey = nodeKey;
        }

        /** 返回父节点键；根节点为 null。 */
        public String getParentNodeKey() { return parentNodeKey; }

        /** 设置父节点键；根节点为 null。 */
        public void setParentNodeKey(String parentNodeKey) {
            this.parentNodeKey = parentNodeKey;
        }

        /** 返回同级顺序。 */
        public Integer getSequenceNo() { return sequenceNo; }

        /** 设置同级顺序。 */
        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }

        /** 返回条件节点类型。 */
        public String getNodeType() { return nodeType; }

        /** 设置条件节点类型。 */
        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        /** 返回分组运算符。 */
        public String getGroupOperator() { return groupOperator; }

        /** 设置分组运算符。 */
        public void setGroupOperator(String groupOperator) {
            this.groupOperator = groupOperator;
        }

        /** 返回比较运算符。 */
        public String getComparisonOperator() { return comparisonOperator; }

        /** 设置比较运算符。 */
        public void setComparisonOperator(String comparisonOperator) {
            this.comparisonOperator = comparisonOperator;
        }

        /** 返回类型化操作数。 */
        public List<OperandRequestBody> getOperands() { return operands; }

        /** 设置类型化操作数。 */
        public void setOperands(List<OperandRequestBody> operands) {
            this.operands = operands == null ? null : List.copyOf(operands);
        }
    }

    /** 条件比较中的历史字段、统一变量或 Literal。 */
    public static class OperandRequestBody {

        /** 操作数角色。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(LEFT|RIGHT)$")
        private String operandRole;

        /** 同一角色内的值顺序。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer valueSequence;

        /** 值来源类型。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(CONTEXT|VARIABLE|LITERAL)$")
        private String sourceType;

        /** 历史 Context 字段编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Size(max = 64) @Pattern(regexp = FIELD_CODE)
        private String fieldCode;

        /** 值类型。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank @Pattern(regexp = "^(STRING|NUMBER|BOOLEAN|DATETIME|DURATION)$")
        private String valueType;

        /** 字面量；只允许 String、Boolean 或 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private Object literalValue;

        /** 统一变量编码。 */
        @Pattern(regexp = CODE)
        private String variableCode;

        /** 数值量纲。 */
        @Pattern(regexp = "^(SCALAR|MONEY)$")
        private String numericUnit;

        /** 固定币种编码。 */
        @Pattern(regexp = "^[A-Z]{3}$")
        private String currencyCode;

        /** 动态币种变量编码。 */
        @Pattern(regexp = CODE)
        private String currencyVariableCode;

        /** JSON 反序列化构造。 */
        public OperandRequestBody() { }

        /** 按完整合同构造。 */
        public OperandRequestBody(String operandRole, Integer valueSequence, String sourceType, String fieldCode, String valueType, Object literalValue, String variableCode, String numericUnit, String currencyCode, String currencyVariableCode) {
            setOperandRole(operandRole);
            setValueSequence(valueSequence);
            setSourceType(sourceType);
            setFieldCode(fieldCode);
            setValueType(valueType);
            setLiteralValue(literalValue);
            setVariableCode(variableCode);
            setNumericUnit(numericUnit);
            setCurrencyCode(currencyCode);
            setCurrencyVariableCode(currencyVariableCode);
        }

        /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
        public OperandRequestBody(String operandRole, Integer valueSequence, String sourceType, String fieldCode,
            String valueType, Object literalValue) {
            this(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue, null, null, null, null);
        }

        /** 返回操作数角色。 */
        public String getOperandRole() { return operandRole; }

        /** 设置操作数角色。 */
        public void setOperandRole(String operandRole) {
            this.operandRole = operandRole;
        }

        /** 返回同一角色内的值顺序。 */
        public Integer getValueSequence() { return valueSequence; }

        /** 设置同一角色内的值顺序。 */
        public void setValueSequence(Integer valueSequence) {
            this.valueSequence = valueSequence;
        }

        /** 返回值来源类型。 */
        public String getSourceType() { return sourceType; }

        /** 设置值来源类型。 */
        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        /** 返回历史 Context 字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置历史 Context 字段编码。 */
        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        /** 返回值类型。 */
        public String getValueType() { return valueType; }

        /** 设置值类型。 */
        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        /** 返回字面量；只允许 String、Boolean 或 null。 */
        public Object getLiteralValue() { return literalValue; }

        /** 设置字面量；只允许 String、Boolean 或 null。 */
        public void setLiteralValue(Object literalValue) {
            if (literalValue != null && !(literalValue instanceof String) && !(literalValue instanceof Boolean))
                throw new IllegalArgumentException("literalValue 必须是 String、Boolean 或 null");
            this.literalValue = literalValue;
        }

        /** 返回统一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置统一变量编码。 */
        public void setVariableCode(String variableCode) {
            this.variableCode = variableCode;
        }

        /** 返回数值量纲。 */
        public String getNumericUnit() { return numericUnit; }

        /** 设置数值量纲。 */
        public void setNumericUnit(String numericUnit) {
            this.numericUnit = numericUnit;
        }

        /** 返回固定币种编码。 */
        public String getCurrencyCode() { return currencyCode; }

        /** 设置固定币种编码。 */
        public void setCurrencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
        }

        /** 返回动态币种变量编码。 */
        public String getCurrencyVariableCode() { return currencyVariableCode; }

        /** 设置动态币种变量编码。 */
        public void setCurrencyVariableCode(String currencyVariableCode) {
            this.currencyVariableCode = currencyVariableCode;
        }
    }

    /** 准确终态动作版本引用。 */
    public static class RuleActionRequestBody {

        /** 动作编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) @Pattern(regexp = CODE)
        private String actionCode;

        /** 准确动作版本号。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer actionVersionNo;

        /** JSON 反序列化构造。 */
        public RuleActionRequestBody() { }

        /** 按完整合同构造。 */
        public RuleActionRequestBody(String actionCode, Integer actionVersionNo) {
            setActionCode(actionCode);
            setActionVersionNo(actionVersionNo);
        }

        /** 返回动作编码。 */
        public String getActionCode() { return actionCode; }

        /** 设置动作编码。 */
        public void setActionCode(String actionCode) {
            this.actionCode = actionCode;
        }

        /** 返回准确动作版本号。 */
        public Integer getActionVersionNo() { return actionVersionNo; }

        /** 设置准确动作版本号。 */
        public void setActionVersionNo(Integer actionVersionNo) {
            this.actionVersionNo = actionVersionNo;
        }
    }

    /** 非终态动作仅保存配置，不执行真实副作用。 */
    public static class AdditionalAction {

        /** 同级顺序。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer sequenceNo;

        /** 动作编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE)
        private String actionCode;

        /** 准确动作版本号。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer actionVersionNo;

        /** 目标绑定编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE)
        private String targetBindingCode;

        /** 动作参数。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Size(min = 1, max = 32)
        private List<@NotNull @Valid ActionParameter> parameters;

        /** 新动作主体指向库中真实 INPUT；历史详情可空。 */
        @Pattern(regexp = CODE)
        private String subjectVariableCode;

        /** JSON 反序列化构造。 */
        public AdditionalAction() { }

        /** 按完整合同构造。 */
        public AdditionalAction(Integer sequenceNo, String actionCode, Integer actionVersionNo, String targetBindingCode, List<ActionParameter> parameters, String subjectVariableCode) {
            setSequenceNo(sequenceNo);
            setActionCode(actionCode);
            setActionVersionNo(actionVersionNo);
            setTargetBindingCode(targetBindingCode);
            setParameters(parameters);
            setSubjectVariableCode(subjectVariableCode);
        }

        /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
        public AdditionalAction(Integer sequenceNo, String actionCode, Integer actionVersionNo,
            String targetBindingCode, List<ActionParameter> parameters) {
            this(sequenceNo, actionCode, actionVersionNo, targetBindingCode, parameters, null);
        }

        /** 返回同级顺序。 */
        public Integer getSequenceNo() { return sequenceNo; }

        /** 设置同级顺序。 */
        public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
        }

        /** 返回动作编码。 */
        public String getActionCode() { return actionCode; }

        /** 设置动作编码。 */
        public void setActionCode(String actionCode) {
            this.actionCode = actionCode;
        }

        /** 返回准确动作版本号。 */
        public Integer getActionVersionNo() { return actionVersionNo; }

        /** 设置准确动作版本号。 */
        public void setActionVersionNo(Integer actionVersionNo) {
            this.actionVersionNo = actionVersionNo;
        }

        /** 返回目标绑定编码。 */
        public String getTargetBindingCode() { return targetBindingCode; }

        /** 设置目标绑定编码。 */
        public void setTargetBindingCode(String targetBindingCode) {
            this.targetBindingCode = targetBindingCode;
        }

        /** 返回动作参数。 */
        public List<ActionParameter> getParameters() { return parameters; }

        /** 设置动作参数。 */
        public void setParameters(List<ActionParameter> parameters) {
            this.parameters = parameters == null ? null : List.copyOf(parameters);
        }

        /** 返回新动作主体指向库中真实 INPUT；历史详情可空。 */
        public String getSubjectVariableCode() { return subjectVariableCode; }

        /** 设置新动作主体指向库中真实 INPUT；历史详情可空。 */
        public void setSubjectVariableCode(String subjectVariableCode) {
            this.subjectVariableCode = subjectVariableCode;
        }
    }

    /** 参数为类型化 Literal 或批准的 Context 引用；实际目标参数由 Risk 生成。 */
    public static class ActionParameter {

        /** 参数编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = CODE)
        private String parameterCode;

        /** 同一角色内的值顺序。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Positive
        private Integer valueSequence;

        /** 值来源类型。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = "^(CONTEXT|LITERAL)$")
        private String sourceType;

        /** 历史 Context 字段编码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @Pattern(regexp = FIELD_CODE)
        private String fieldCode;

        /** 值类型。 */
        @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank
        @Pattern(regexp = "^(STRING|NUMBER|BOOLEAN|DATETIME|DURATION)$")
        private String valueType;

        /** 字面量；只允许 String、Boolean 或 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        private Object literalValue;

        /** JSON 反序列化构造。 */
        public ActionParameter() { }

        /** 按完整合同构造。 */
        public ActionParameter(String parameterCode, Integer valueSequence, String sourceType, String fieldCode, String valueType, Object literalValue) {
            setParameterCode(parameterCode);
            setValueSequence(valueSequence);
            setSourceType(sourceType);
            setFieldCode(fieldCode);
            setValueType(valueType);
            setLiteralValue(literalValue);
        }

        /** 返回参数编码。 */
        public String getParameterCode() { return parameterCode; }

        /** 设置参数编码。 */
        public void setParameterCode(String parameterCode) {
            this.parameterCode = parameterCode;
        }

        /** 返回同一角色内的值顺序。 */
        public Integer getValueSequence() { return valueSequence; }

        /** 设置同一角色内的值顺序。 */
        public void setValueSequence(Integer valueSequence) {
            this.valueSequence = valueSequence;
        }

        /** 返回值来源类型。 */
        public String getSourceType() { return sourceType; }

        /** 设置值来源类型。 */
        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        /** 返回历史 Context 字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置历史 Context 字段编码。 */
        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        /** 返回值类型。 */
        public String getValueType() { return valueType; }

        /** 设置值类型。 */
        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        /** 返回字面量；只允许 String、Boolean 或 null。 */
        public Object getLiteralValue() { return literalValue; }

        /** 设置字面量；只允许 String、Boolean 或 null。 */
        public void setLiteralValue(Object literalValue) {
            if (literalValue != null && !(literalValue instanceof String) && !(literalValue instanceof Boolean))
                throw new IllegalArgumentException("literalValue 必须是 String、Boolean 或 null");
            this.literalValue = literalValue;
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    /** 批准的决策点执行能力目录。 */
    public static class DecisionPointDescriptorResponseBody {

        /** 决策事件点编码。 */
        private String pointCode;

        /** 决策事件点名称。 */
        private String pointName;

        /** 业务域。 */
        private String businessDomain;

        /** 输入合同编码。 */
        private String inputContractCode;

        /** 准确输入合同版本。 */
        private Integer inputContractVersion;

        /** 输出合同编码。 */
        private String outputContractCode;

        /** 默认决定。 */
        private String defaultDecision;

        /** 评估策略。 */
        private String evaluationStrategy;

        /** 失败策略。 */
        private String failureStrategy;

        /** 超时上限，单位毫秒。 */
        private Integer timeoutMillis;

        /** 当前状态。 */
        private String status;

        /** JSON 反序列化构造。 */
        public DecisionPointDescriptorResponseBody() { }

        /** 按完整合同构造。 */
        public DecisionPointDescriptorResponseBody(String pointCode, String pointName, String businessDomain, String inputContractCode, Integer inputContractVersion, String outputContractCode, String defaultDecision, String evaluationStrategy, String failureStrategy, Integer timeoutMillis, String status) {
            setPointCode(pointCode);
            setPointName(pointName);
            setBusinessDomain(businessDomain);
            setInputContractCode(inputContractCode);
            setInputContractVersion(inputContractVersion);
            setOutputContractCode(outputContractCode);
            setDefaultDecision(defaultDecision);
            setEvaluationStrategy(evaluationStrategy);
            setFailureStrategy(failureStrategy);
            setTimeoutMillis(timeoutMillis);
            setStatus(status);
        }

        /** 返回决策事件点编码。 */
        public String getPointCode() { return pointCode; }

        /** 设置决策事件点编码。 */
        public void setPointCode(String pointCode) {
            this.pointCode = pointCode;
        }

        /** 返回决策事件点名称。 */
        public String getPointName() { return pointName; }

        /** 设置决策事件点名称。 */
        public void setPointName(String pointName) {
            this.pointName = pointName;
        }

        /** 返回业务域。 */
        public String getBusinessDomain() { return businessDomain; }

        /** 设置业务域。 */
        public void setBusinessDomain(String businessDomain) {
            this.businessDomain = businessDomain;
        }

        /** 返回输入合同编码。 */
        public String getInputContractCode() { return inputContractCode; }

        /** 设置输入合同编码。 */
        public void setInputContractCode(String inputContractCode) {
            this.inputContractCode = inputContractCode;
        }

        /** 返回准确输入合同版本。 */
        public Integer getInputContractVersion() { return inputContractVersion; }

        /** 设置准确输入合同版本。 */
        public void setInputContractVersion(Integer inputContractVersion) {
            this.inputContractVersion = inputContractVersion;
        }

        /** 返回输出合同编码。 */
        public String getOutputContractCode() { return outputContractCode; }

        /** 设置输出合同编码。 */
        public void setOutputContractCode(String outputContractCode) {
            this.outputContractCode = outputContractCode;
        }

        /** 返回默认决定。 */
        public String getDefaultDecision() { return defaultDecision; }

        /** 设置默认决定。 */
        public void setDefaultDecision(String defaultDecision) {
            this.defaultDecision = defaultDecision;
        }

        /** 返回评估策略。 */
        public String getEvaluationStrategy() { return evaluationStrategy; }

        /** 设置评估策略。 */
        public void setEvaluationStrategy(String evaluationStrategy) {
            this.evaluationStrategy = evaluationStrategy;
        }

        /** 返回失败策略。 */
        public String getFailureStrategy() { return failureStrategy; }

        /** 设置失败策略。 */
        public void setFailureStrategy(String failureStrategy) {
            this.failureStrategy = failureStrategy;
        }

        /** 返回超时上限，单位毫秒。 */
        public Integer getTimeoutMillis() { return timeoutMillis; }

        /** 设置超时上限，单位毫秒。 */
        public void setTimeoutMillis(Integer timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        /** 返回当前状态。 */
        public String getStatus() { return status; }

        /** 设置当前状态。 */
        public void setStatus(String status) {
            this.status = status;
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    /** 输入字段能力及对应系统变量。 */
    public static class DecisionInputFieldDescriptorResponseBody {

        /** 历史 Context 字段编码。 */
        private String fieldCode;

        /** 值类型。 */
        private String valueType;

        /** 是否必填。 */
        private Boolean requiredFlag;

        /** 数据分级。 */
        private String dataClassification;

        /** 是否可被规则引用。 */
        private Boolean ruleReferenceFlag;

        /** 是否可作动作参数。 */
        private Boolean actionParameterFlag;

        /** 统一变量编码。 */
        private String variableCode;

        /** JSON 反序列化构造。 */
        public DecisionInputFieldDescriptorResponseBody() { }

        /** 按完整合同构造。 */
        public DecisionInputFieldDescriptorResponseBody(String fieldCode, String valueType, Boolean requiredFlag, String dataClassification, Boolean ruleReferenceFlag, Boolean actionParameterFlag, String variableCode) {
            setFieldCode(fieldCode);
            setValueType(valueType);
            setRequiredFlag(requiredFlag);
            setDataClassification(dataClassification);
            setRuleReferenceFlag(ruleReferenceFlag);
            setActionParameterFlag(actionParameterFlag);
            setVariableCode(variableCode);
        }

        /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
        public DecisionInputFieldDescriptorResponseBody(String fieldCode, String valueType, Boolean requiredFlag,
            String dataClassification, Boolean ruleReferenceFlag, Boolean actionParameterFlag) {
            this(fieldCode, valueType, requiredFlag, dataClassification, ruleReferenceFlag, actionParameterFlag, null);
        }

        /** 返回历史 Context 字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置历史 Context 字段编码。 */
        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        /** 返回值类型。 */
        public String getValueType() { return valueType; }

        /** 设置值类型。 */
        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        /** 返回是否必填。 */
        public Boolean getRequiredFlag() { return requiredFlag; }

        /** 设置是否必填。 */
        public void setRequiredFlag(Boolean requiredFlag) {
            this.requiredFlag = requiredFlag;
        }

        /** 返回数据分级。 */
        public String getDataClassification() { return dataClassification; }

        /** 设置数据分级。 */
        public void setDataClassification(String dataClassification) {
            this.dataClassification = dataClassification;
        }

        /** 返回是否可被规则引用。 */
        public Boolean getRuleReferenceFlag() { return ruleReferenceFlag; }

        /** 设置是否可被规则引用。 */
        public void setRuleReferenceFlag(Boolean ruleReferenceFlag) {
            this.ruleReferenceFlag = ruleReferenceFlag;
        }

        /** 返回是否可作动作参数。 */
        public Boolean getActionParameterFlag() { return actionParameterFlag; }

        /** 设置是否可作动作参数。 */
        public void setActionParameterFlag(Boolean actionParameterFlag) {
            this.actionParameterFlag = actionParameterFlag;
        }

        /** 返回统一变量编码。 */
        public String getVariableCode() { return variableCode; }

        /** 设置统一变量编码。 */
        public void setVariableCode(String variableCode) {
            this.variableCode = variableCode;
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    /** 动作实现的准确版本与配置能力。 */
    public static class DecisionActionDescriptorResponseBody {

        /** 动作编码。 */
        private String actionCode;

        /** 动作名称。 */
        private String actionName;

        /** 动作类别。 */
        private String actionCategory;

        /** 业务版本号。 */
        private Integer versionNo;

        /** 执行器编码。 */
        private String handlerCode;

        /** 实现版本。 */
        private String implementationVersion;

        /** 实现内容 Hash。 */
        private String implementationHash;

        /** 是否为终态动作。 */
        private Boolean terminalFlag;

        /** 终态决定代码。 */
        private String decisionCode;

        /** 合并策略。 */
        private String mergePolicy;

        /** 是否支持影子执行。 */
        private Boolean shadowSupportedFlag;

        /** 当前状态。 */
        private String status;

        /** JSON 反序列化构造。 */
        public DecisionActionDescriptorResponseBody() { }

        /** 按完整合同构造。 */
        public DecisionActionDescriptorResponseBody(String actionCode, String actionName, String actionCategory, Integer versionNo, String handlerCode, String implementationVersion, String implementationHash, Boolean terminalFlag, String decisionCode, String mergePolicy, Boolean shadowSupportedFlag, String status) {
            setActionCode(actionCode);
            setActionName(actionName);
            setActionCategory(actionCategory);
            setVersionNo(versionNo);
            setHandlerCode(handlerCode);
            setImplementationVersion(implementationVersion);
            setImplementationHash(implementationHash);
            setTerminalFlag(terminalFlag);
            setDecisionCode(decisionCode);
            setMergePolicy(mergePolicy);
            setShadowSupportedFlag(shadowSupportedFlag);
            setStatus(status);
        }

        /** 返回动作编码。 */
        public String getActionCode() { return actionCode; }

        /** 设置动作编码。 */
        public void setActionCode(String actionCode) {
            this.actionCode = actionCode;
        }

        /** 返回动作名称。 */
        public String getActionName() { return actionName; }

        /** 设置动作名称。 */
        public void setActionName(String actionName) {
            this.actionName = actionName;
        }

        /** 返回动作类别。 */
        public String getActionCategory() { return actionCategory; }

        /** 设置动作类别。 */
        public void setActionCategory(String actionCategory) {
            this.actionCategory = actionCategory;
        }

        /** 返回业务版本号。 */
        public Integer getVersionNo() { return versionNo; }

        /** 设置业务版本号。 */
        public void setVersionNo(Integer versionNo) {
            this.versionNo = versionNo;
        }

        /** 返回执行器编码。 */
        public String getHandlerCode() { return handlerCode; }

        /** 设置执行器编码。 */
        public void setHandlerCode(String handlerCode) {
            this.handlerCode = handlerCode;
        }

        /** 返回实现版本。 */
        public String getImplementationVersion() { return implementationVersion; }

        /** 设置实现版本。 */
        public void setImplementationVersion(String implementationVersion) {
            this.implementationVersion = implementationVersion;
        }

        /** 返回实现内容 Hash。 */
        public String getImplementationHash() { return implementationHash; }

        /** 设置实现内容 Hash。 */
        public void setImplementationHash(String implementationHash) {
            this.implementationHash = implementationHash;
        }

        /** 返回是否为终态动作。 */
        public Boolean getTerminalFlag() { return terminalFlag; }

        /** 设置是否为终态动作。 */
        public void setTerminalFlag(Boolean terminalFlag) {
            this.terminalFlag = terminalFlag;
        }

        /** 返回终态决定代码。 */
        public String getDecisionCode() { return decisionCode; }

        /** 设置终态决定代码。 */
        public void setDecisionCode(String decisionCode) {
            this.decisionCode = decisionCode;
        }

        /** 返回合并策略。 */
        public String getMergePolicy() { return mergePolicy; }

        /** 设置合并策略。 */
        public void setMergePolicy(String mergePolicy) {
            this.mergePolicy = mergePolicy;
        }

        /** 返回是否支持影子执行。 */
        public Boolean getShadowSupportedFlag() { return shadowSupportedFlag; }

        /** 设置是否支持影子执行。 */
        public void setShadowSupportedFlag(Boolean shadowSupportedFlag) {
            this.shadowSupportedFlag = shadowSupportedFlag;
        }

        /** 返回当前状态。 */
        public String getStatus() { return status; }

        /** 设置当前状态。 */
        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 当前 Point 批准的完整主体绑定，包含命名空间等必要组成部分。 */
    public static class TargetBindingDescriptor {

        /** 主体绑定编码。 */
        private String bindingCode;

        /** 显示名称。 */
        private String displayName;

        /** 主体类型。 */
        private String subjectType;

        /** 主体所需字段。 */
        private List<TargetFieldDescriptor> requiredFields;

        /** 主体语义角色。 */
        private String subjectSemanticRole;

        /** JSON 反序列化构造。 */
        public TargetBindingDescriptor() { }

        /** 按完整合同构造。 */
        public TargetBindingDescriptor(String bindingCode, String displayName, String subjectType, List<TargetFieldDescriptor> requiredFields, String subjectSemanticRole) {
            setBindingCode(bindingCode);
            setDisplayName(displayName);
            setSubjectType(subjectType);
            setRequiredFields(requiredFields);
            setSubjectSemanticRole(subjectSemanticRole);
        }

        /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
        public TargetBindingDescriptor(String bindingCode, String displayName, String subjectType, List<TargetFieldDescriptor> requiredFields) {
            this(bindingCode, displayName, subjectType, requiredFields, null);
        }

        /** 返回主体绑定编码。 */
        public String getBindingCode() { return bindingCode; }

        /** 设置主体绑定编码。 */
        public void setBindingCode(String bindingCode) {
            this.bindingCode = bindingCode;
        }

        /** 返回显示名称。 */
        public String getDisplayName() { return displayName; }

        /** 设置显示名称。 */
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        /** 返回主体类型。 */
        public String getSubjectType() { return subjectType; }

        /** 设置主体类型。 */
        public void setSubjectType(String subjectType) {
            this.subjectType = subjectType;
        }

        /** 返回主体所需字段。 */
        public List<TargetFieldDescriptor> getRequiredFields() { return requiredFields; }

        /** 设置主体所需字段。 */
        public void setRequiredFields(List<TargetFieldDescriptor> requiredFields) {
            this.requiredFields = requiredFields == null ? null : List.copyOf(requiredFields);
        }

        /** 返回主体语义角色。 */
        public String getSubjectSemanticRole() { return subjectSemanticRole; }

        /** 设置主体语义角色。 */
        public void setSubjectSemanticRole(String subjectSemanticRole) {
            this.subjectSemanticRole = subjectSemanticRole;
        }
    }

    /** 主体绑定中的字段角色与历史编码。 */
    public static class TargetFieldDescriptor {

        /** 字段角色。 */
        private String role;

        /** 历史 Context 字段编码。 */
        private String fieldCode;

        /** JSON 反序列化构造。 */
        public TargetFieldDescriptor() { }

        /** 按完整合同构造。 */
        public TargetFieldDescriptor(String role, String fieldCode) {
            setRole(role);
            setFieldCode(fieldCode);
        }

        /** 返回字段角色。 */
        public String getRole() { return role; }

        /** 设置字段角色。 */
        public void setRole(String role) {
            this.role = role;
        }

        /** 返回历史 Context 字段编码。 */
        public String getFieldCode() { return fieldCode; }

        /** 设置历史 Context 字段编码。 */
        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }
    }

    /** 参数值域由 Risk 定义；时长上下限为毫秒字符串。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ActionParameterDescriptor {

        /** 动作编码。 */
        private String actionCode;

        /** 准确动作版本号。 */
        private Integer actionVersionNo;

        /** 参数编码。 */
        private String parameterCode;

        /** 值类型。 */
        private String valueType;

        /** 是否必填。 */
        private Boolean requiredFlag;

        /** 允许的参数来源。 */
        private String allowedSourceType;

        /** 是否为系统生成的目标参数。 */
        private Boolean targetParameterFlag;

        /** 最大参数值数量。 */
        private Integer maxValues;

        /** 最小值，时长以毫秒字符串表示。 */
        private String minimumValue;

        /** 最大值，时长以毫秒字符串表示。 */
        private String maximumValue;

        /** 字符串取值格式。 */
        private String valuePattern;

        /** JSON 反序列化构造。 */
        public ActionParameterDescriptor() { }

        /** 按完整合同构造。 */
        public ActionParameterDescriptor(String actionCode, Integer actionVersionNo, String parameterCode, String valueType, Boolean requiredFlag, String allowedSourceType, Boolean targetParameterFlag, Integer maxValues, String minimumValue, String maximumValue, String valuePattern) {
            setActionCode(actionCode);
            setActionVersionNo(actionVersionNo);
            setParameterCode(parameterCode);
            setValueType(valueType);
            setRequiredFlag(requiredFlag);
            setAllowedSourceType(allowedSourceType);
            setTargetParameterFlag(targetParameterFlag);
            setMaxValues(maxValues);
            setMinimumValue(minimumValue);
            setMaximumValue(maximumValue);
            setValuePattern(valuePattern);
        }

        /** 返回动作编码。 */
        public String getActionCode() { return actionCode; }

        /** 设置动作编码。 */
        public void setActionCode(String actionCode) {
            this.actionCode = actionCode;
        }

        /** 返回准确动作版本号。 */
        public Integer getActionVersionNo() { return actionVersionNo; }

        /** 设置准确动作版本号。 */
        public void setActionVersionNo(Integer actionVersionNo) {
            this.actionVersionNo = actionVersionNo;
        }

        /** 返回参数编码。 */
        public String getParameterCode() { return parameterCode; }

        /** 设置参数编码。 */
        public void setParameterCode(String parameterCode) {
            this.parameterCode = parameterCode;
        }

        /** 返回值类型。 */
        public String getValueType() { return valueType; }

        /** 设置值类型。 */
        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        /** 返回是否必填。 */
        public Boolean getRequiredFlag() { return requiredFlag; }

        /** 设置是否必填。 */
        public void setRequiredFlag(Boolean requiredFlag) {
            this.requiredFlag = requiredFlag;
        }

        /** 返回允许的参数来源。 */
        public String getAllowedSourceType() { return allowedSourceType; }

        /** 设置允许的参数来源。 */
        public void setAllowedSourceType(String allowedSourceType) {
            this.allowedSourceType = allowedSourceType;
        }

        /** 返回是否为系统生成的目标参数。 */
        public Boolean getTargetParameterFlag() { return targetParameterFlag; }

        /** 设置是否为系统生成的目标参数。 */
        public void setTargetParameterFlag(Boolean targetParameterFlag) {
            this.targetParameterFlag = targetParameterFlag;
        }

        /** 返回最大参数值数量。 */
        public Integer getMaxValues() { return maxValues; }

        /** 设置最大参数值数量。 */
        public void setMaxValues(Integer maxValues) {
            this.maxValues = maxValues;
        }

        /** 返回最小值，时长以毫秒字符串表示。 */
        public String getMinimumValue() { return minimumValue; }

        /** 设置最小值，时长以毫秒字符串表示。 */
        public void setMinimumValue(String minimumValue) {
            this.minimumValue = minimumValue;
        }

        /** 返回最大值，时长以毫秒字符串表示。 */
        public String getMaximumValue() { return maximumValue; }

        /** 设置最大值，时长以毫秒字符串表示。 */
        public void setMaximumValue(String maximumValue) {
            this.maximumValue = maximumValue;
        }

        /** 返回字符串取值格式。 */
        public String getValuePattern() { return valuePattern; }

        /** 设置字符串取值格式。 */
        public void setValuePattern(String valuePattern) {
            this.valuePattern = valuePattern;
        }
    }

}
