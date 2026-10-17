package com.paytech.risk.decision.api.evaluation.response;

import java.util.List;

/** 评估中一个变量的取值。 */
public class EvaluationVariableItem {

    /** 变量编码。 */
    private String variableCode;

    /** 变量名称。 */
    private String variableName;

    /** 来源：INPUT、CONSTANT、COLLECTION、VELOCITY、LIST。 */
    private String sourceType;

    /** 值类型。 */
    private String valueType;

    /**
     * 标量规范值。
     * <li>没有值、Feature 失败或集合变量为空。</li>
     */
    private String value;

    /**
     * 集合当前成员规范值。
     * <li>非集合变量为空。</li>
     */
    private List<String> values;

    /** 取值依据：EVALUATION 评估时的值；CURRENT 当前值（常量、集合），可能与评估时不同。 */
    private String valueBasis;

    /**
     * Feature 技术状态：SUCCEEDED、FAILED。
     * <li>非实时累计、名单命中变量为空。</li>
     */
    private String technicalStatus;

    /**
     * Feature 失败代码。
     * <li>成功或非 Feature 变量为空。</li>
     */
    private String failureCode;

    /**
     * Feature 计算耗时毫秒。
     * <li>非 Feature 变量为空。</li>
     */
    private Integer elapsedMillis;

    /** 返回变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 返回变量名称。 */
    public String getVariableName() { return variableName; }

    /** 设置变量名称。 */
    public void setVariableName(String variableName) { this.variableName = variableName; }

    /** 返回来源。 */
    public String getSourceType() { return sourceType; }

    /** 设置来源。 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 返回值类型。 */
    public String getValueType() { return valueType; }

    /** 设置值类型。 */
    public void setValueType(String valueType) { this.valueType = valueType; }

    /** 返回标量规范值。 */
    public String getValue() { return value; }

    /** 设置标量规范值。 */
    public void setValue(String value) { this.value = value; }

    /** 返回集合当前成员规范值。 */
    public List<String> getValues() { return values; }

    /** 设置集合当前成员规范值。 */
    public void setValues(List<String> values) { this.values = values; }

    /** 返回取值依据。 */
    public String getValueBasis() { return valueBasis; }

    /** 设置取值依据。 */
    public void setValueBasis(String valueBasis) { this.valueBasis = valueBasis; }

    /** 返回Feature 技术状态。 */
    public String getTechnicalStatus() { return technicalStatus; }

    /** 设置Feature 技术状态。 */
    public void setTechnicalStatus(String technicalStatus) { this.technicalStatus = technicalStatus; }

    /** 返回Feature 失败代码。 */
    public String getFailureCode() { return failureCode; }

    /** 设置Feature 失败代码。 */
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    /** 返回Feature 计算耗时毫秒。 */
    public Integer getElapsedMillis() { return elapsedMillis; }

    /** 设置Feature 计算耗时毫秒。 */
    public void setElapsedMillis(Integer elapsedMillis) { this.elapsedMillis = elapsedMillis; }
}
