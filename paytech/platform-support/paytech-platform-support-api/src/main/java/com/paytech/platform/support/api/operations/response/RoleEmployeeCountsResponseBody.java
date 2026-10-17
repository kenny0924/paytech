package com.paytech.platform.support.api.operations.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class RoleEmployeeCountsResponseBody {
    /**
     * 按请求角色顺序返回的计数条目。
     * <li>包含零计数。</li>
     */
    private final List<RoleEmployeeCount> items;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleEmployeeCountsResponseBody(@JsonProperty(value = "items") List<RoleEmployeeCount> items) {
        this.items = items == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
    }

    /** 按请求角色顺序返回的计数条目，包含零计数。 */
    public List<RoleEmployeeCount> getItems() {
        return items;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleEmployeeCountsResponseBody other && Objects.equals(items, other.items);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(items);
    }
}
