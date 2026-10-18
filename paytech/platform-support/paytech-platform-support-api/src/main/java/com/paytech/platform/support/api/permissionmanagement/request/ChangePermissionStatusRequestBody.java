package com.paytech.platform.support.api.permissionmanagement.request;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 切换权限状态；保留原有关联，管理入口引用的权限不可停用。 */
public final class ChangePermissionStatusRequestBody {
    /** 权限状态：ACTIVE 或 INACTIVE。 */
    @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE")
    private final String status;

    /**
     * 调用方读取的原版本。
     * <li>必须为正整数；版本不符返回并发冲突。</li>
     */
    @Positive
    private final int expectedVersion;

    /**
     * 操作原因。
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public ChangePermissionStatusRequestBody(@JsonProperty(value = "status") String status,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty(value = "reason") String reason) {
        this.status = status;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 权限状态：ACTIVE 或 INACTIVE。 */
    public String getStatus() { return status; }

    /**
     * 调用方读取的原版本。
     * <li>必须为正整数；版本不符返回并发冲突。</li>
     */
    public int getExpectedVersion() { return expectedVersion; }

    /**
     * 操作原因。
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    public String getReason() { return reason; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChangePermissionStatusRequestBody value)) return false;
        return Objects.equals(status, value.status)
            && Objects.equals(expectedVersion, value.expectedVersion)
            && Objects.equals(reason, value.reason);
    }

    @Override
    public int hashCode() { return Objects.hash(status, expectedVersion, reason); }
}
