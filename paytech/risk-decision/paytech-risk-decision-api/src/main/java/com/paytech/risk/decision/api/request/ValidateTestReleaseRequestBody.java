package com.paytech.risk.api.decision.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.*;

/** TEST Candidate 的受控协议结构。 */
public class ValidateTestReleaseRequestBody {

    /** 调用方读取的 Candidate 原聚合版本，必须为正整数。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 供普通 JSON 属性绑定使用。 */
    public ValidateTestReleaseRequestBody() { }

    /** 根据完整协议字段构造。 */
    public ValidateTestReleaseRequestBody(Integer expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    /** 返回调用方读取的 Candidate 原聚合版本，必须为正整数。 */
    public Integer getExpectedVersion() { return expectedVersion; }

    /** 设置调用方读取的 Candidate 原聚合版本，必须为正整数。 */
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }
}
