package com.paytech.platform.support.api.operations.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class RoleEmployeeCount {
    /**
     * 角色 ID。
     * <li>十进制字符串。</li>
     */
    private final String roleId;

    /**
     * 关联的 Operations 人员数量。
     * <li>包含停用账号、暂停成员及未来生效的有效关联。</li>
     * <li>最小为 0。</li>
     */
    private final long employeeCount;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleEmployeeCount(@JsonProperty(value = "roleId") String roleId,
        @JsonProperty(value = "employeeCount", required = true) long employeeCount) {
        this.roleId = roleId;
        this.employeeCount = employeeCount;
    }

    /** 角色 ID，十进制字符串。 */
    public String getRoleId() {
        return roleId;
    }

    /** 关联的 Operations 人员数量，包含停用账号、暂停成员及未来生效的有效关联；最小为 0。 */
    public long getEmployeeCount() {
        return employeeCount;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleEmployeeCount other && Objects.equals(roleId, other.roleId)
            && Objects.equals(employeeCount, other.employeeCount);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(roleId, employeeCount);
    }
}
