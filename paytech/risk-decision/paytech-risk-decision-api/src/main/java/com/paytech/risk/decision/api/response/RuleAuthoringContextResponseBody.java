package com.paytech.risk.api.decision.response;

import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameterDescriptor;
import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody;
import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody;
import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionPointDescriptorResponseBody;
import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.TargetBindingDescriptor;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ActionParameterDescriptor;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionActionDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionInputFieldDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.DecisionPointDescriptorResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.TargetBindingDescriptor;
import com.paytech.risk.api.decision.RiskVariableModels;

/** 规则集管理响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RuleAuthoringContextResponseBody {

    /** 配置能力描述符编码。 */
    private String descriptorCode;

    /** 决策事件点能力。 */
    private DecisionPointDescriptorResponseBody point;

    /** 输入字段目录。 */
    private List<DecisionInputFieldDescriptorResponseBody> fields;

    /** 支持的比较运算符。 */
    private List<String> operators;

    /** 批准的动作目录。 */
    private List<DecisionActionDescriptorResponseBody> actions;

    /** 当前配置不提供 Feature，返回空列表。 */
    private List<Void> features;

    /** 动作参数定义。 */
    private List<ActionParameterDescriptor> actionParameters;

    /** 当前配置不提供 Obligation，返回空列表。 */
    private List<Void> obligations;

    /** 当前配置不提供限额绑定，返回空列表。 */
    private List<Void> limitBindings;

    /** 批准的主体绑定。 */
    private List<TargetBindingDescriptor> targetBindings;

    /** 操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    private String operandFormat;

    /** 变量查询作用域。 */
    private RiskVariableModels.VariableQueryContext variableQueryContext;

    /** JSON 反序列化构造。 */
    public RuleAuthoringContextResponseBody() { }

    /** 按完整合同构造。 */
    public RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point, List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators, List<DecisionActionDescriptorResponseBody> actions, List<Void> features, List<ActionParameterDescriptor> actionParameters, List<Void> obligations, List<Void> limitBindings, List<TargetBindingDescriptor> targetBindings, String operandFormat, RiskVariableModels.VariableQueryContext variableQueryContext) {
        setDescriptorCode(descriptorCode);
        setPoint(point);
        setFields(fields);
        setOperators(operators);
        setActions(actions);
        setFeatures(features);
        setActionParameters(actionParameters);
        setObligations(obligations);
        setLimitBindings(limitBindings);
        setTargetBindings(targetBindings);
        setOperandFormat(operandFormat);
        setVariableQueryContext(variableQueryContext);
    }

    /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
    public RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point,
        List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
        List<DecisionActionDescriptorResponseBody> actions, List<Void> features,
        List<ActionParameterDescriptor> actionParameters, List<Void> obligations, List<Void> limitBindings,
        List<TargetBindingDescriptor> targetBindings) {
        this(descriptorCode, point, fields, operators, actions, features, actionParameters, obligations,
            limitBindings, targetBindings, null, null);
    }

    /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
    public RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point,
        List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
        List<DecisionActionDescriptorResponseBody> actions, List<Void> features,
        List<ActionParameterDescriptor> actionParameters, List<Void> obligations, List<Void> limitBindings) {
        this(descriptorCode, point, fields, operators, actions, features, actionParameters, obligations,
            limitBindings, List.of());
    }

    /** 返回配置能力描述符编码。 */
    public String getDescriptorCode() { return descriptorCode; }

    /** 设置配置能力描述符编码。 */
    public void setDescriptorCode(String descriptorCode) {
        this.descriptorCode = descriptorCode;
    }

    /** 返回决策事件点能力。 */
    public DecisionPointDescriptorResponseBody getPoint() { return point; }

    /** 设置决策事件点能力。 */
    public void setPoint(DecisionPointDescriptorResponseBody point) {
        this.point = point;
    }

    /** 返回输入字段目录。 */
    public List<DecisionInputFieldDescriptorResponseBody> getFields() { return fields; }

    /** 设置输入字段目录。 */
    public void setFields(List<DecisionInputFieldDescriptorResponseBody> fields) {
        this.fields = fields == null ? null : List.copyOf(fields);
    }

    /** 返回支持的比较运算符。 */
    public List<String> getOperators() { return operators; }

    /** 设置支持的比较运算符。 */
    public void setOperators(List<String> operators) {
        this.operators = operators == null ? null : List.copyOf(operators);
    }

    /** 返回批准的动作目录。 */
    public List<DecisionActionDescriptorResponseBody> getActions() { return actions; }

    /** 设置批准的动作目录。 */
    public void setActions(List<DecisionActionDescriptorResponseBody> actions) {
        this.actions = actions == null ? null : List.copyOf(actions);
    }

    /** 返回当前配置不提供 Feature，返回空列表。 */
    public List<Void> getFeatures() { return features; }

    /** 设置当前配置不提供 Feature，返回空列表。 */
    public void setFeatures(List<Void> features) {
        this.features = features == null ? null : List.copyOf(features);
    }

    /** 返回动作参数定义。 */
    public List<ActionParameterDescriptor> getActionParameters() { return actionParameters; }

    /** 设置动作参数定义。 */
    public void setActionParameters(List<ActionParameterDescriptor> actionParameters) {
        this.actionParameters = actionParameters == null ? null : List.copyOf(actionParameters);
    }

    /** 返回当前配置不提供 Obligation，返回空列表。 */
    public List<Void> getObligations() { return obligations; }

    /** 设置当前配置不提供 Obligation，返回空列表。 */
    public void setObligations(List<Void> obligations) {
        this.obligations = obligations == null ? null : List.copyOf(obligations);
    }

    /** 返回当前配置不提供限额绑定，返回空列表。 */
    public List<Void> getLimitBindings() { return limitBindings; }

    /** 设置当前配置不提供限额绑定，返回空列表。 */
    public void setLimitBindings(List<Void> limitBindings) {
        this.limitBindings = limitBindings == null ? null : List.copyOf(limitBindings);
    }

    /** 返回批准的主体绑定。 */
    public List<TargetBindingDescriptor> getTargetBindings() { return targetBindings; }

    /** 设置批准的主体绑定。 */
    public void setTargetBindings(List<TargetBindingDescriptor> targetBindings) {
        this.targetBindings = targetBindings == null ? null : List.copyOf(targetBindings);
    }

    /** 返回操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    public String getOperandFormat() { return operandFormat; }

    /** 设置操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    public void setOperandFormat(String operandFormat) {
        this.operandFormat = operandFormat;
    }

    /** 返回变量查询作用域。 */
    public RiskVariableModels.VariableQueryContext getVariableQueryContext() { return variableQueryContext; }

    /** 设置变量查询作用域。 */
    public void setVariableQueryContext(RiskVariableModels.VariableQueryContext variableQueryContext) {
        this.variableQueryContext = variableQueryContext;
    }
}
