package com.paytech.platform.support.api.operations.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class PermissionCatalogResponseBody {
    /** 当前操作者可授予的有效权限目录。 */
    private final List<PermissionSummary> permissions;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public PermissionCatalogResponseBody(@JsonProperty(value = "permissions") List<PermissionSummary> permissions) {
        this.permissions = permissions == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(permissions));
    }

    /** 当前操作者可授予的有效权限目录。 */
    public List<PermissionSummary> getPermissions() {
        return permissions;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof PermissionCatalogResponseBody other && Objects.equals(permissions, other.permissions);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(permissions);
    }
}
