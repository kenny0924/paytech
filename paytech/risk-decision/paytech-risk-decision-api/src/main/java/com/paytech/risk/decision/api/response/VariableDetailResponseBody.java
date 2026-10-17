package com.paytech.risk.api.decision.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 当前详情包含来源元数据，不保存输入运行值或共享值历史。 */
public class VariableDetailResponseBody {

    /** 永久唯一变量编码。 */
    private String variableCode;

    /** 运营展示名称。 */
    private String variableName;

    /** 变量来源类型。 */
    private String sourceType;

    /** 变量或成员的标量类型。 */
    private String valueType;

    /** 适用决策点；全局变量可空。 */
    private String pointCode;

    /** 允许的引用用途。 */
    private List<String> usages;

    /** 是否为工程保留定义。 */
    private boolean systemManaged;

    /** 当前状态。 */
    private String status;

    /** 根 CAS 版本，以正整数字符串表示。 */
    private String version;

    /** NUMBER 的量纲；其他类型为空。 */
    private String numericUnit;

    /** 固定币种；与动态币种变量互斥。 */
    private String currencyCode;

    /** 动态币种变量编码；不适用时为空。 */
    private String currencyVariableCode;

    /** 通用值格式代码。 */
    private String formatCode;

    /** 可选值集合编码；无额外约束时为空。 */
    private String allowedValuesVariableCode;

    /** 常量当前值；仅为字符串或 Boolean，其他来源为空。 */
    private Object currentValue;

    /** 集合成员数量；非集合为空。 */
    private Long memberCount;

    /** 有界成员预览。 */
    private List<Member> previewMembers;

    /** UTC 微秒更新时间。 */
    private String updateTime;

    /** 非敏感说明；可空。 */
    private String description;

    /** 准确来源合同代码；不适用时为空。 */
    private String sourceContractCode;

    /** 准确来源合同版本；不取最新版本。 */
    private Integer sourceContractVersion;

    /** 来源合同顶层字段；不适用时为空。 */
    private String sourceField;

    /** 创建操作者 ID 字符串。 */
    private String createBy;

    /** 最近操作者 ID 字符串。 */
    private String updateBy;

    /** UTC 微秒创建时间。 */
    private String createTime;

    /** 当前值读取时间，不参与配置内容 Hash。 */
    private String valuesAsOf;

    /** 可选值集合当前版本及有限预览；未关联时为空。 */
    private AllowedValues allowedValues;

    /** JSON 反序列化构造。 */
    public VariableDetailResponseBody() { }

    /** 按完整合同构造。 */
    public VariableDetailResponseBody(String variableCode, String variableName, String sourceType, String valueType, String pointCode, List<String> usages, boolean systemManaged, String status, String version, String numericUnit, String currencyCode, String currencyVariableCode, String formatCode, String allowedValuesVariableCode, Object currentValue, Long memberCount, List<Member> previewMembers, String updateTime, String description, String sourceContractCode, Integer sourceContractVersion, String sourceField, String createBy, String updateBy, String createTime, String valuesAsOf, AllowedValues allowedValues) {
        this.variableCode = variableCode;
        this.variableName = variableName;
        this.sourceType = sourceType;
        this.valueType = valueType;
        this.pointCode = pointCode;
        this.usages = usages;
        this.systemManaged = systemManaged;
        this.status = status;
        this.version = version;
        this.numericUnit = numericUnit;
        this.currencyCode = currencyCode;
        this.currencyVariableCode = currencyVariableCode;
        this.formatCode = formatCode;
        this.allowedValuesVariableCode = allowedValuesVariableCode;
        this.currentValue = currentValue;
        this.memberCount = memberCount;
        this.previewMembers = previewMembers;
        this.updateTime = updateTime;
        this.description = description;
        this.sourceContractCode = sourceContractCode;
        this.sourceContractVersion = sourceContractVersion;
        this.sourceField = sourceField;
        this.createBy = createBy;
        this.updateBy = updateBy;
        this.createTime = createTime;
        this.valuesAsOf = valuesAsOf;
        this.allowedValues = allowedValues;
    }

    /** 读取永久唯一变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置永久唯一变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 读取运营展示名称。 */
    public String getVariableName() { return variableName; }

    /** 设置运营展示名称。 */
    public void setVariableName(String variableName) { this.variableName = variableName; }

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

    /** 读取允许的引用用途。 */
    public List<String> getUsages() { return usages; }

    /** 设置允许的引用用途。 */
    public void setUsages(List<String> usages) { this.usages = usages; }

    /** 读取是否为工程保留定义。 */
    public boolean getSystemManaged() { return systemManaged; }

    /** 设置是否为工程保留定义。 */
    public void setSystemManaged(boolean systemManaged) { this.systemManaged = systemManaged; }

    /** 读取当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取根 CAS 版本，以正整数字符串表示。 */
    public String getVersion() { return version; }

    /** 设置根 CAS 版本，以正整数字符串表示。 */
    public void setVersion(String version) { this.version = version; }

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

    /** 读取通用值格式代码。 */
    public String getFormatCode() { return formatCode; }

    /** 设置通用值格式代码。 */
    public void setFormatCode(String formatCode) { this.formatCode = formatCode; }

    /** 读取可选值集合编码。 */
    public String getAllowedValuesVariableCode() { return allowedValuesVariableCode; }

    /** 设置可选值集合编码。 */
    public void setAllowedValuesVariableCode(String allowedValuesVariableCode) { this.allowedValuesVariableCode = allowedValuesVariableCode; }

    /** 读取常量当前值。 */
    public Object getCurrentValue() { return currentValue; }

    /** 设置常量当前值。 */
    public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }

    /** 读取集合成员数量。 */
    public Long getMemberCount() { return memberCount; }

    /** 设置集合成员数量。 */
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }

    /** 读取有界成员预览。 */
    public List<Member> getPreviewMembers() { return previewMembers; }

    /** 设置有界成员预览。 */
    public void setPreviewMembers(List<Member> previewMembers) { this.previewMembers = previewMembers; }

    /** 读取UTC 微秒更新时间。 */
    public String getUpdateTime() { return updateTime; }

    /** 设置UTC 微秒更新时间。 */
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    /** 读取非敏感说明。 */
    public String getDescription() { return description; }

    /** 设置非敏感说明。 */
    public void setDescription(String description) { this.description = description; }

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

    /** 读取创建操作者 ID 字符串。 */
    public String getCreateBy() { return createBy; }

    /** 设置创建操作者 ID 字符串。 */
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    /** 读取最近操作者 ID 字符串。 */
    public String getUpdateBy() { return updateBy; }

    /** 设置最近操作者 ID 字符串。 */
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    /** 读取UTC 微秒创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置UTC 微秒创建时间。 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    /** 读取当前值读取时间，不参与配置内容 Hash。 */
    public String getValuesAsOf() { return valuesAsOf; }

    /** 设置当前值读取时间，不参与配置内容 Hash。 */
    public void setValuesAsOf(String valuesAsOf) { this.valuesAsOf = valuesAsOf; }

    /** 读取可选值集合当前版本及有限预览。 */
    public AllowedValues getAllowedValues() { return allowedValues; }

    /** 设置可选值集合当前版本及有限预览。 */
    public void setAllowedValues(AllowedValues allowedValues) { this.allowedValues = allowedValues; }
}
