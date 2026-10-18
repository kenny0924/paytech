package com.paytech.risk.decision.api.request;

import static com.paytech.risk.decision.api.DecisionRuleSetManagementModels.CONTENT_HASH;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 规则集管理请求。 */
public class FreezeRuleSetVersionRequestBody {

    /** 冻结前读取的规则内容 Hash。 */
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotNull @Pattern(regexp = CONTENT_HASH)
    private String expectedContentHash;

    /** JSON 反序列化构造。 */
    public FreezeRuleSetVersionRequestBody() { }

    /** 按完整合同构造。 */
    public FreezeRuleSetVersionRequestBody(String expectedContentHash) {
        setExpectedContentHash(expectedContentHash);
    }

    /** 返回冻结前读取的规则内容 Hash。 */
    public String getExpectedContentHash() { return expectedContentHash; }

    /** 设置冻结前读取的规则内容 Hash。 */
    public void setExpectedContentHash(String expectedContentHash) {
        this.expectedContentHash = expectedContentHash;
    }
}
