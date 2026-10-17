package com.paytech.platform.support.api.permissionmanagement.request;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建权限；初始状态固定 INACTIVE，身份字段不可变。 */
public final class CreatePermissionRequestBody {
    /**
     * 不可变权限编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 128 字符。</li>
     */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private final String permissionCode;

    /**
     * 不可变资源类型编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String resourceType;

    /**
     * 不可变动作编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String actionCode;

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL")
    private final String riskLevel;

    /**
     * 操作原因。
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public CreatePermissionRequestBody(@JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "riskLevel") String riskLevel,
        @JsonProperty(value = "reason") String reason) {
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.riskLevel = riskLevel;
        this.reason = reason;
    }

    /**
     * 不可变权限编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 128 字符。</li>
     */
    public String getPermissionCode() { return permissionCode; }

    /**
     * 不可变资源类型编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    public String getResourceType() { return resourceType; }

    /**
     * 不可变动作编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    public String getActionCode() { return actionCode; }

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    public String getRiskLevel() { return riskLevel; }

    /**
     * 操作原因。
     * <li>必填，最多 500 字符；保存时去除首尾空白。</li>
     */
    public String getReason() { return reason; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CreatePermissionRequestBody value)) return false;
        return Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(actionCode, value.actionCode)
            && Objects.equals(riskLevel, value.riskLevel)
            && Objects.equals(reason, value.reason);
    }

    @Override
    public int hashCode() { return Objects.hash(permissionCode, resourceType, actionCode, riskLevel, reason); }
}
