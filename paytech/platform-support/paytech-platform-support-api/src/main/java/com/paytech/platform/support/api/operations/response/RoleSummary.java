package com.paytech.platform.support.api.operations.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class RoleSummary {
    /**
     * 角色 ID。
     * <li>十进制字符串。</li>
     */
    private final String roleId;

    /**
     * 不可变角色代码。
     * <li>1 至 64 个大写字母、数字或下划线。</li>
     * <li>首字符为字母。</li>
     */
    private final String roleCode;

    /**
     * 角色名称。
     * <li>去除首尾空白后为 1 至 255 个字符。</li>
     */
    private final String roleName;

    /** 是否为禁止普通管理操作修改的系统角色。 */
    private final boolean systemRole;

    /** 当前账号或角色聚合版本。 */
    private final int version;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleSummary(@JsonProperty(value = "roleId") String roleId,
        @JsonProperty(value = "roleCode") String roleCode,
        @JsonProperty(value = "roleName") String roleName,
        @JsonProperty(value = "systemRole", required = true) boolean systemRole,
        @JsonProperty(value = "version", required = true) int version) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.systemRole = systemRole;
        this.version = version;
    }

    /** 角色 ID，十进制字符串。 */
    public String getRoleId() {
        return roleId;
    }

    /** 不可变角色代码，1 至 64 个大写字母、数字或下划线，首字符为字母。 */
    public String getRoleCode() {
        return roleCode;
    }

    /** 角色名称，去除首尾空白后为 1 至 255 个字符。 */
    public String getRoleName() {
        return roleName;
    }

    /** 是否为禁止普通管理操作修改的系统角色。 */
    public boolean getSystemRole() {
        return systemRole;
    }

    /** 当前账号或角色聚合版本。 */
    public int getVersion() {
        return version;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleSummary other && Objects.equals(roleId, other.roleId)
            && Objects.equals(roleCode, other.roleCode)
            && Objects.equals(roleName, other.roleName)
            && Objects.equals(systemRole, other.systemRole)
            && Objects.equals(version, other.version);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(roleId, roleCode, roleName, systemRole, version);
    }
}
