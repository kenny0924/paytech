package com.paytech.platform.support.api.operations.request;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Operations 请求字段，保持既有 JSON 合同。 */
public final class RoleEmployeeCountsRequestBody {
    /**
     * 角色 ID 列表。
     * <li>不允许重复。</li>
     * <li>人员授权必填 1 至 20 项。</li>
     * <li>计数查询为 1 至 200 项。</li>
     */
    @NotEmpty @Size(max = 200)
    private final List<@NotBlank String> roleIds;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleEmployeeCountsRequestBody(@JsonProperty(value = "roleIds") List<String> roleIds) {
        this.roleIds = roleIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(roleIds));
    }

    /** 角色 ID 列表，不允许重复；人员授权必填 1 至 20 项，计数查询为 1 至 200 项。 */
    public List<String> getRoleIds() {
        return roleIds;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleEmployeeCountsRequestBody other && Objects.equals(roleIds, other.roleIds);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(roleIds);
    }
}
