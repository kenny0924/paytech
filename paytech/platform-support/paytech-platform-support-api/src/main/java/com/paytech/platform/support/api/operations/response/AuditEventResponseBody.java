package com.paytech.platform.support.api.operations.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class AuditEventResponseBody {
    /** Operations 范围内的审计核心事实。 */
    private final AuditEvent event;

    /**
     * 按原始序号排列的安全属性。
     * <li>保留重复键。</li>
     */
    private final List<AuditAttribute> attributes;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public AuditEventResponseBody(@JsonProperty(value = "event") AuditEvent event,
        @JsonProperty(value = "attributes") List<AuditAttribute> attributes) {
        this.event = event;
        this.attributes = attributes == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attributes));
    }

    /** Operations 范围内的审计核心事实。 */
    public AuditEvent getEvent() {
        return event;
    }

    /** 按原始序号排列的安全属性，保留重复键。 */
    public List<AuditAttribute> getAttributes() {
        return attributes;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof AuditEventResponseBody other && Objects.equals(event, other.event)
            && Objects.equals(attributes, other.attributes);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(event, attributes);
    }
}
