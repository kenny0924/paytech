package com.paytech.risk.api.decision.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;

import com.paytech.risk.api.decision.DecisionReleaseManagementModels.*;

/** TEST Candidate 的受控协议结构。 */
public class CreateTestReleaseRequestBody {

    /** 准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String ruleSetVersionId;

    /** 调用方读取的规则内容 SHA-256，小写十六进制 64 位。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Pattern(regexp = "^[0-9a-f]{64}$")
    private String expectedRuleSetContentHash;

    /** 供普通 JSON 属性绑定使用。 */
    public CreateTestReleaseRequestBody() { }

    /** 根据完整协议字段构造。 */
    public CreateTestReleaseRequestBody(String ruleSetVersionId, String expectedRuleSetContentHash) {
        this.ruleSetVersionId = ruleSetVersionId;
        this.expectedRuleSetContentHash = expectedRuleSetContentHash;
    }

    /** 返回准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置准确冻结规则版本 ID，以正 BIGINT 十进制字符串传输。 */
    public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

    /** 返回调用方读取的规则内容 SHA-256，小写十六进制 64 位。 */
    public String getExpectedRuleSetContentHash() { return expectedRuleSetContentHash; }

    /** 设置调用方读取的规则内容 SHA-256，小写十六进制 64 位。 */
    public void setExpectedRuleSetContentHash(String expectedRuleSetContentHash) { this.expectedRuleSetContentHash = expectedRuleSetContentHash; }
}
