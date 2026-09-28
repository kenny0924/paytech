package com.paytech.platform.support.api.operations.request;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Operations 请求字段，保持既有 JSON 合同。 */
public final class CreateRoleRequestBody {
    /**
     * 不可变角色代码。
     * <li>1 至 64 个大写字母、数字或下划线。</li>
     * <li>首字符为字母。</li>
     */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String roleCode;

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
     * 必填操作原因。
     * <li>最长 500 个字符。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public CreateRoleRequestBody(@JsonProperty(value = "roleCode") String roleCode,
        @JsonProperty(value = "roleName") String roleName,
        @JsonProperty(value = "permissionIds") List<String> permissionIds,
        @JsonProperty(value = "reason") String reason) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.permissionIds = permissionIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(permissionIds));
        this.reason = reason;
    }

    /** 不可变角色代码，1 至 64 个大写字母、数字或下划线，首字符为字母。 */
    public String getRoleCode() {
        return roleCode;
    }

    /** 角色名称，去除首尾空白后为 1 至 255 个字符。 */
    public String getRoleName() {
        return roleName;
    }

    /** 完整权限 ID 集合，不允许重复；可显式提交空列表。 */
    public List<String> getPermissionIds() {
        return permissionIds;
    }

    /** 必填操作原因，最长 500 个字符。 */
    public String getReason() {
        return reason;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof CreateRoleRequestBody other && Objects.equals(roleCode, other.roleCode)
            && Objects.equals(roleName, other.roleName)
            && Objects.equals(permissionIds, other.permissionIds)
            && Objects.equals(reason, other.reason);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(roleCode, roleName, permissionIds, reason);
    }
}
