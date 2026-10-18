package com.paytech.platform.support.api.operations.request;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Operations 请求字段，保持既有 JSON 合同。 */
public final class UpdateRoleRequestBody {
    /**
     * 角色名称。
     * <li>去除首尾空白后为 1 至 255 个字符。</li>
     */
    @NotBlank @Size(max = 255)
    private final String roleName;

    /**
     * 完整权限 ID 集合。
     * <li>不允许重复。</li>
     * <li>可显式提交空列表。</li>
     */
    @NotNull
    private final List<@NotBlank String> permissionIds;

    /**
     * 期望的当前账号或角色版本。
     * <li>必须为正整数。</li>
     */
    @Positive
    private final int expectedVersion;

    /**
     * 必填操作原因。
     * <li>最长 500 个字符。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public UpdateRoleRequestBody(@JsonProperty(value = "roleName") String roleName,
        @JsonProperty(value = "permissionIds") List<String> permissionIds,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty(value = "reason") String reason) {
        this.roleName = roleName;
        this.permissionIds = permissionIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(permissionIds));
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** 角色名称，去除首尾空白后为 1 至 255 个字符。 */
    public String getRoleName() {
        return roleName;
    }

    /** 完整权限 ID 集合，不允许重复；可显式提交空列表。 */
    public List<String> getPermissionIds() {
        return permissionIds;
    }

    /** 期望的当前账号或角色版本，必须为正整数。 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 必填操作原因，最长 500 个字符。 */
    public String getReason() {
        return reason;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof UpdateRoleRequestBody other && Objects.equals(roleName, other.roleName)
            && Objects.equals(permissionIds, other.permissionIds)
            && Objects.equals(expectedVersion, other.expectedVersion)
            && Objects.equals(reason, other.reason);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(roleName, permissionIds, expectedVersion, reason);
    }
}
