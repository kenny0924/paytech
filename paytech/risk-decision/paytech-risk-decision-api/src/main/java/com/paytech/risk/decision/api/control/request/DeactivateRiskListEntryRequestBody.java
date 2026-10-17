package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工失效名单成员的请求。 */
public class DeactivateRiskListEntryRequestBody {

    /** 失效原因，最长 500 字符。 */
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 读取失效原因。 */
    public String getReason() { return reason; }

    /** 设置失效原因。 */
    public void setReason(String reason) { this.reason = reason; }
}
