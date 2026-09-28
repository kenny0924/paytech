package com.paytech.platform.support.api.permissionmanagement.request;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 更新风险等级；不改变权限身份和状态。 */
public final class UpdatePermissionRequestBody {
    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL")
    private final String riskLevel;

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
    public UpdatePermissionRequestBody(@JsonProperty(value = "riskLevel") String riskLevel,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty(value = "reason") String reason) {
        this.riskLevel = riskLevel;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    public String getRiskLevel() { return riskLevel; }

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
        if (!(other instanceof UpdatePermissionRequestBody value)) return false;
        return Objects.equals(riskLevel, value.riskLevel)
            && Objects.equals(expectedVersion, value.expectedVersion)
            && Objects.equals(reason, value.reason);
    }

    @Override
    public int hashCode() { return Objects.hash(riskLevel, expectedVersion, reason); }
}
