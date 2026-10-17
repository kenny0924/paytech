package com.paytech.risk.api.decision.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 创建后结构固定；所有不适用字段显式 null，系统身份和审计字段由服务端提供。 */
public class CreateVariableRequestBody {

    /** 永久唯一变量编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String variableCode;

    /** 运营展示名称。 */
    @NotBlank
    private String variableName;

    /** 非敏感说明；可空。 */
    private String description;

    /** 变量来源类型。 */
    @NotBlank
    private String sourceType;

    /** 变量或成员的标量类型。 */
    @NotBlank
    private String valueType;

    /** 适用决策点；全局变量可空。 */
    private String pointCode;

    /** 准确来源合同代码；不适用时为空。 */
    private String sourceContractCode;

    /** 准确来源合同版本；不取最新版本。 */
    @Positive
    private Integer sourceContractVersion;

    /** 来源合同顶层字段；不适用时为空。 */
    private String sourceField;

    /** 允许的引用用途。 */
    @NotNull @Size(min = 1, max = 3)
    private List<@NotBlank String> usages;

    /** 通用值格式代码。 */
    @NotBlank
    private String formatCode;

    /** NUMBER 的量纲；其他类型为空。 */
    private String numericUnit;

    /** 固定币种；与动态币种变量互斥。 */
    private String currencyCode;

    /** 动态币种变量编码；不适用时为空。 */
    private String currencyVariableCode;

    /** 可选值集合编码；无额外约束时为空。 */
    private String allowedValuesVariableCode;

    /** 常量当前值；仅为字符串或 Boolean，其他来源为空。 */
    private Object currentValue;

    /** 当前状态。 */
    @NotBlank
    private String status;

    /** JSON 反序列化构造。 */
    public CreateVariableRequestBody() { }

    /** 按完整合同构造。 */
    public CreateVariableRequestBody(String variableCode, String variableName, String description, String sourceType, String valueType, String pointCode, String sourceContractCode, Integer sourceContractVersion, String sourceField, List<String> usages, String formatCode, String numericUnit, String currencyCode, String currencyVariableCode, String allowedValuesVariableCode, Object currentValue, String status) {
        this.variableCode = variableCode;
        this.variableName = variableName;
        this.description = description;
        this.sourceType = sourceType;
        this.valueType = valueType;
        this.pointCode = pointCode;
        this.sourceContractCode = sourceContractCode;
        this.sourceContractVersion = sourceContractVersion;
        this.sourceField = sourceField;
        this.usages = usages;
        this.formatCode = formatCode;
        this.numericUnit = numericUnit;
        this.currencyCode = currencyCode;
        this.currencyVariableCode = currencyVariableCode;
        this.allowedValuesVariableCode = allowedValuesVariableCode;
        this.currentValue = currentValue;
        this.status = status;
    }

    /**
     * 实时累计参数
     * <li>仅 sourceType=VELOCITY 必填，其他来源必须为空；创建后不可修改。</li>
     */
    @Valid
    private VelocityFeatureParameters velocity;

    /**
     * 名单命中参数
     * <li>仅 sourceType=LIST 必填，其他来源必须为空；创建后不可修改。</li>
     */
    @Valid
    private ListFeatureParameters list;

    /** 读取永久唯一变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置永久唯一变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 读取运营展示名称。 */
    public String getVariableName() { return variableName; }

    /** 设置运营展示名称。 */
    public void setVariableName(String variableName) { this.variableName = variableName; }

    /** 读取非敏感说明。 */
    public String getDescription() { return description; }

    /** 设置非敏感说明。 */
    public void setDescription(String description) { this.description = description; }

    /** 读取变量来源类型。 */
    public String getSourceType() { return sourceType; }

    /** 设置变量来源类型。 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 读取变量或成员的标量类型。 */
    public String getValueType() { return valueType; }

    /** 设置变量或成员的标量类型。 */
    public void setValueType(String valueType) { this.valueType = valueType; }

    /** 读取适用决策点。 */
    public String getPointCode() { return pointCode; }

    /** 设置适用决策点。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 读取准确来源合同代码。 */
    public String getSourceContractCode() { return sourceContractCode; }

    /** 设置准确来源合同代码。 */
    public void setSourceContractCode(String sourceContractCode) { this.sourceContractCode = sourceContractCode; }

    /** 读取准确来源合同版本。 */
    public Integer getSourceContractVersion() { return sourceContractVersion; }

    /** 设置准确来源合同版本。 */
    public void setSourceContractVersion(Integer sourceContractVersion) { this.sourceContractVersion = sourceContractVersion; }

    /** 读取来源合同顶层字段。 */
    public String getSourceField() { return sourceField; }

    /** 设置来源合同顶层字段。 */
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }

    /** 读取允许的引用用途。 */
    public List<String> getUsages() { return usages; }

    /** 设置允许的引用用途。 */
    public void setUsages(List<String> usages) { this.usages = usages; }

    /** 读取通用值格式代码。 */
    public String getFormatCode() { return formatCode; }

    /** 设置通用值格式代码。 */
    public void setFormatCode(String formatCode) { this.formatCode = formatCode; }

    /** 读取NUMBER 的量纲。 */
    public String getNumericUnit() { return numericUnit; }

    /** 设置NUMBER 的量纲。 */
    public void setNumericUnit(String numericUnit) { this.numericUnit = numericUnit; }

    /** 读取固定币种。 */
    public String getCurrencyCode() { return currencyCode; }

    /** 设置固定币种。 */
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    /** 读取动态币种变量编码。 */
    public String getCurrencyVariableCode() { return currencyVariableCode; }

    /** 设置动态币种变量编码。 */
    public void setCurrencyVariableCode(String currencyVariableCode) { this.currencyVariableCode = currencyVariableCode; }

    /** 读取可选值集合编码。 */
    public String getAllowedValuesVariableCode() { return allowedValuesVariableCode; }

    /** 设置可选值集合编码。 */
    public void setAllowedValuesVariableCode(String allowedValuesVariableCode) { this.allowedValuesVariableCode = allowedValuesVariableCode; }

    /** 读取常量当前值。 */
    public Object getCurrentValue() { return currentValue; }

    /** 设置常量当前值。 */
    public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }

    /** 读取当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取实时累计参数。 */
    public VelocityFeatureParameters getVelocity() { return velocity; }

    /** 设置实时累计参数。 */
    public void setVelocity(VelocityFeatureParameters velocity) { this.velocity = velocity; }

    /** 读取名单命中参数。 */
    public ListFeatureParameters getList() { return list; }

    /** 设置名单命中参数。 */
    public void setList(ListFeatureParameters list) { this.list = list; }
}
