package com.paytech.risk.api.decision.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 字典维护请求，完整提交本次内容，不提供历史响应回放。 */
public class ChangeDictionaryStatusRequestBody {

    /**
     * 期望修订号。
     * <li>最近读取的正 BIGINT 十进制字符串，必须与当前修订号相同。</li>
     */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807")
    private String expectedVersion;

    /**
     * 目标字典资格。
     * <li>ACTIVE 或 DISABLED；成功提交递增版本，已有引用不阻止停用。</li>
     */
    @NotNull
    @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;

    /** 返回期望修订号。 */
    public String getExpectedVersion() {
        return expectedVersion;
    }

    /** 设置期望修订号。 */
    public void setExpectedVersion(String expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    /** 返回目标字典资格。 */
    public String getStatus() {
        return status;
    }

    /** 设置目标字典资格。 */
    public void setStatus(String status) {
        this.status = status;
    }
}

