package com.paytech.risk.decision.api.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.decision.api.RiskVariableModels.*;

/** 使用期望根版本维护当前值，禁止提交结构字段。 */
public class UpdateVariableRequestBody {

    /** 调用者确认的正整数根版本。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String expectedVersion;

    /** 运营展示名称。 */
    @NotBlank
    private String variableName;

    /** 非敏感说明；可空。 */
    private String description;

    /** 常量当前值；仅为字符串或 Boolean，其他来源为空。 */
    private Object currentValue;

    /** 当前状态。 */
    @NotBlank
    private String status;

    /** JSON 反序列化构造。 */
    public UpdateVariableRequestBody() { }

    /** 按完整合同构造。 */
    public UpdateVariableRequestBody(String expectedVersion, String variableName, String description, Object currentValue, String status) {
        this.expectedVersion = expectedVersion;
        this.variableName = variableName;
        this.description = description;
        this.currentValue = currentValue;
        this.status = status;
    }

    /** 读取调用者确认的正整数根版本。 */
    public String getExpectedVersion() { return expectedVersion; }

    /** 设置调用者确认的正整数根版本。 */
    public void setExpectedVersion(String expectedVersion) { this.expectedVersion = expectedVersion; }

    /** 读取运营展示名称。 */
    public String getVariableName() { return variableName; }

    /** 设置运营展示名称。 */
    public void setVariableName(String variableName) { this.variableName = variableName; }

    /** 读取非敏感说明。 */
    public String getDescription() { return description; }

    /** 设置非敏感说明。 */
    public void setDescription(String description) { this.description = description; }

    /** 读取常量当前值。 */
    public Object getCurrentValue() { return currentValue; }

    /** 设置常量当前值。 */
    public void setCurrentValue(Object currentValue) { this.currentValue = currentValue; }

    /** 读取当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) { this.status = status; }
}
