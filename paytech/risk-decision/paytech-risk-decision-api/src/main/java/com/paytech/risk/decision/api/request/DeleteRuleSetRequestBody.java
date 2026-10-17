package com.paytech.risk.api.decision.request;

import static com.paytech.risk.api.decision.DecisionRuleSetManagementModels.POSITIVE_DECIMAL_ID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 规则集管理请求。 */
public class DeleteRuleSetRequestBody {

    /** 正 BIGINT 十进制文本；空值或超出范围不得进入业务层。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Pattern(regexp = POSITIVE_DECIMAL_ID)
    @DecimalMax("9223372036854775807")
    private String expectedVersion;

    /** JSON 反序列化构造。 */
    public DeleteRuleSetRequestBody() { }

    /** 按完整合同构造。 */
    public DeleteRuleSetRequestBody(String expectedVersion) {
        setExpectedVersion(expectedVersion);
    }

    /** 返回正 BIGINT 十进制文本；空值或超出范围不得进入业务层。 */
    public String getExpectedVersion() { return expectedVersion; }

    /** 设置正 BIGINT 十进制文本；空值或超出范围不得进入业务层。 */
    public void setExpectedVersion(String expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
