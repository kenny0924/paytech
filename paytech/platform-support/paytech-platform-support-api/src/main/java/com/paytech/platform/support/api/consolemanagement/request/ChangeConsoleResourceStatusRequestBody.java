package com.paytech.platform.support.api.consolemanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 停用父级保留子项本身状态与全部引用。 */
public final class ChangeConsoleResourceStatusRequestBody {
    /** 当前或请求的配置状态。 */
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED")
    private final String status;

    /** 调用方期望的当前版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因或维护限制说明。 */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ChangeConsoleResourceStatusRequestBody(@JsonProperty(value = "status") String status,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty(value = "reason") String reason) {
        this.status = status;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 当前或请求的配置状态。 */
    public String getStatus() {
        return status;
    }

    /** 调用方期望的当前版本。 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 操作原因或维护限制说明。 */
    public String getReason() {
        return reason;
    }
}
