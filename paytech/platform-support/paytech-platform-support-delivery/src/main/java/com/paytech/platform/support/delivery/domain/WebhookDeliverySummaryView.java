package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;

import lombok.Getter;
import lombok.Setter;

/** 投递列表项：投递与所属事件的公开 eventId 组合查询结果。 */
@Getter
@Setter
public class WebhookDeliverySummaryView {

    /** 投递主键。 */
    private Long deliveryId;

    /** 公开 eventId。 */
    private String eventId;

    /** 事件类型。 */
    private String eventType;

    /** 目标端点。 */
    private Long endpointId;

    /** 投递状态。 */
    private WebhookDeliveryStatus status;

    /** 已创建的尝试数量。 */
    private int attemptCount;

    /** 最近一次尝试开始时间。 */
    private Instant lastAttemptTime;

    /** Replay 来源投递。 */
    private Long replayOfDeliveryId;

    /** 创建时间。 */
    private Instant createTime;
}
