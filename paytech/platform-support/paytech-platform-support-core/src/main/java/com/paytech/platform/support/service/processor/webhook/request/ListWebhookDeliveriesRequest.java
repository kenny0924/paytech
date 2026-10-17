package com.paytech.platform.support.service.processor.webhook.request;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 投递分页查询；租户由 Client ID 在处理器内解析。 */
@Getter
@AllArgsConstructor
public class ListWebhookDeliveriesRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** CCT Client ID。 */
    private final long clientId;

    /** 已校验的分页。 */
    private final PagingRequestBody paging;

    /** 创建时间下限（含）。 */
    private final Instant createdFrom;

    /** 创建时间上限（不含），跨度不超过 31 天。 */
    private final Instant createdTo;

    /** 可选端点。 */
    private final Long endpointId;

    /** 可选事件类型。 */
    private final String eventType;

    /** 可选投递状态。 */
    private final WebhookDeliveryStatus status;

    /** 可选公开 eventId。 */
    private final String eventId;
}
