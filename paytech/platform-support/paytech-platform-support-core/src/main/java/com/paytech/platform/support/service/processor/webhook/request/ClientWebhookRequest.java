package com.paytech.platform.support.service.processor.webhook.request;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 工作区自助 Webhook 内部请求；按操作使用其中的字段
 * <li>工作区只取自可信账号与会话，请求中没有 Client 或租户字段。</li>
 */
@Getter
@Builder
@AllArgsConstructor
public final class ClientWebhookRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;
    /** 目标端点。 */
    private final Long endpointId;
    /** 目标投递。 */
    private final Long deliveryId;
    /** 已通过语法校验的接收地址。 */
    private final String url;
    /** 已校验的订阅事件类型。 */
    private final List<String> eventTypes;
    /** 调用方看到的版本。 */
    private final int expectedVersion;
    /** 目标端点状态。 */
    private final WebhookEndpointStatus status;
    /** 轮换时是否立即作废旧密钥。 */
    private final boolean immediate;
    /** 投递查询起始时间（含）。 */
    private final Instant createdFrom;
    /** 投递查询结束时间（不含）。 */
    private final Instant createdTo;
    /** 投递事件类型筛选。 */
    private final String eventType;
    /** 投递状态筛选。 */
    private final WebhookDeliveryStatus deliveryStatus;
    /** 分页。 */
    private final PagingRequestBody paging;

    /** 不输出会话坐标与地址。 */
    @Override public String toString() { return "ClientWebhookRequest[REDACTED]"; }
}
