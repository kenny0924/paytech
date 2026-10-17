package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营投递列表的已校验筛选条件；时间区间左闭右开。 */
@Getter
@AllArgsConstructor
public class WebhookDeliveryFilter {

    /** 必填 CLIENT 租户。 */
    private final long tenantId;

    /** 创建时间下限（含）。 */
    private final Instant createdFrom;

    /** 创建时间上限（不含）。 */
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
