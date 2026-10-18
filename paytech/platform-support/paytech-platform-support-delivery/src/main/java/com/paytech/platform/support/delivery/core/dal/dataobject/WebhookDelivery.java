package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;

import lombok.Getter;
import lombok.Setter;

/** 一个事件到一个端点的投递；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookDelivery {

    /** 投递主键，也是补偿任务事件键。 */
    private Long deliveryId;

    /** 所属事件。 */
    private Long webhookEventId;

    /** 所属 CLIENT 租户。 */
    private Long tenantId;

    /** 目标端点。 */
    private Long endpointId;

    /** 冗余事件类型。 */
    private String eventType;

    /** 投递状态。 */
    private WebhookDeliveryStatus status;

    /** 已创建的尝试数量。 */
    private int attemptCount;

    /** 最近一次尝试开始时间；尚未尝试时为空。 */
    private Instant lastAttemptTime;

    /** Replay 来源投递；原始投递为空。 */
    private Long replayOfDeliveryId;

    /** Replay 操作人账号；原始投递为空。 */
    private Long requestedBy;

    /** 条件更新版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
