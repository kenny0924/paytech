package com.paytech.platform.support.api.operations.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class RoleResourcesResponseBody {
    /**
     * 平面资源目录。
     * <li>页面树由 BFF 组装。</li>
     */
    private final List<RoleResource> resources;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleResourcesResponseBody(@JsonProperty(value = "resources") List<RoleResource> resources) {
        this.resources = resources == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(resources));
    }

    /** 平面资源目录，页面树由 BFF 组装。 */
    public List<RoleResource> getResources() {
        return resources;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleResourcesResponseBody other && Objects.equals(resources, other.resources);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(resources);
    }
}
