package com.paytech.platform.support.api.operations.response;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class AuditEvent {
    /**
     * 审计事件 ID。
     * <li>十进制字符串。</li>
     */
    private final String auditEventId;

    /**
     * 操作者 ID。
     * <li>筛选时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String actorId;

    /**
     * 操作代码。
     * <li>查询时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String actionCode;

    /** 资源类型 Code。 */
    private final String resourceType;

    /**
     * 资源 ID。
     * <li>审计筛选时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String resourceId;

    /**
     * 审计结果 Code。
     * <li>筛选时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String result;

    /**
     * 请求追踪 ID。
     * <li>筛选时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String requestId;

    /**
     * 审计发生时刻。
     * <li>UTC。</li>
     * <li>精度为微秒。</li>
     */
    private final Instant occurredTime;

    /**
     * 必填操作原因。
     * <li>最长 500 个字符。</li>
     */
    private final String reason;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public AuditEvent(@JsonProperty(value = "auditEventId") String auditEventId,
        @JsonProperty(value = "actorId") String actorId,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "resourceId") String resourceId,
        @JsonProperty(value = "result") String result,
        @JsonProperty(value = "requestId") String requestId,
        @JsonProperty(value = "occurredTime") Instant occurredTime,
        @JsonProperty(value = "reason") String reason) {
        this.auditEventId = auditEventId;
        this.actorId = actorId;
        this.actionCode = actionCode;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result;
        this.requestId = requestId;
        this.occurredTime = occurredTime;
        this.reason = reason;
    }

    /** 审计事件 ID，十进制字符串。 */
    public String getAuditEventId() {
        return auditEventId;
    }

    /** 操作者 ID；筛选时可空，非空时精确匹配。 */
    public String getActorId() {
        return actorId;
    }

    /** 操作代码，查询时可空；非空时精确匹配。 */
    public String getActionCode() {
        return actionCode;
    }

    /** 资源类型 Code。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 资源 ID；审计筛选时可空，非空时精确匹配。 */
    public String getResourceId() {
        return resourceId;
    }

    /** 审计结果 Code；筛选时可空，非空时精确匹配。 */
    public String getResult() {
        return result;
    }

    /** 请求追踪 ID；筛选时可空，非空时精确匹配。 */
    public String getRequestId() {
        return requestId;
    }

    /** 审计发生时刻，UTC，精度为微秒。 */
    public Instant getOccurredTime() {
        return occurredTime;
    }

    /** 必填操作原因，最长 500 个字符。 */
    public String getReason() {
        return reason;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof AuditEvent other && Objects.equals(auditEventId, other.auditEventId)
            && Objects.equals(actorId, other.actorId)
            && Objects.equals(actionCode, other.actionCode)
            && Objects.equals(resourceType, other.resourceType)
            && Objects.equals(resourceId, other.resourceId)
            && Objects.equals(result, other.result)
            && Objects.equals(requestId, other.requestId)
            && Objects.equals(occurredTime, other.occurredTime)
            && Objects.equals(reason, other.reason);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(auditEventId, actorId, actionCode, resourceType, resourceId, result, requestId, occurredTime, reason);
    }
}
