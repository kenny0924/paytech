package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前配置关联计数，不构成跨表一致性快照。 */
public final class ReferenceCounts {
    /** 引用该权限的资源数量。 */
    private final long resource;

    /** 引用该权限的路由数量。 */
    private final long route;

    /** 未撤销的角色授权关联数量。 */
    private final long role;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public ReferenceCounts(@JsonProperty(value = "resource", required = true) long resource,
        @JsonProperty(value = "route", required = true) long route,
        @JsonProperty(value = "role", required = true) long role) {
        this.resource = resource;
        this.route = route;
        this.role = role;
    }

    /** 引用该权限的资源数量。 */
    public long getResource() { return resource; }

    /** 引用该权限的路由数量。 */
    public long getRoute() { return route; }

    /** 未撤销的角色授权关联数量。 */
    public long getRole() { return role; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReferenceCounts value)) return false;
        return Objects.equals(resource, value.resource)
            && Objects.equals(route, value.route)
            && Objects.equals(role, value.role);
    }

    @Override
    public int hashCode() { return Objects.hash(resource, route, role); }
}
