package com.paytech.operations.console.bff.service.model.externalapi.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 发起一次沙箱验证运行。 */
public class StartVerifyRunRequestBody {

    /**
     * 运行原因
     * <li>随运行快照保存，说明本次验证的目的。</li>
     */
    @NotBlank
    @Size(max = 500)
    private final String reason;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StartVerifyRunRequestBody(
        @JsonProperty("reason") String reason) {
        this.reason = reason;
    }

    /** 运行原因 */
    public String getReason() {
        return reason;
    }
}
