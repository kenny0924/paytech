package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.Getter;
import lombok.Setter;

/** 投递列表项。 */
@Getter
@Setter
public class WebhookDeliverySummaryResponseBody {

    /** 投递 ID，字符串形式。 */
    private String deliveryId;

    /** 公开 eventId。 */
    private String eventId;

    /** 事件类型。 */
    private String eventType;

    /** 目标端点 ID，字符串形式。 */
    private String endpointId;

    /** 投递状态：CREATED、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD、CANCELLED。 */
    private String status;

    /** 已创建的尝试数量。 */
    private int attemptCount;

    /** 最近一次尝试开始的 UTC 时间；尚未尝试时为空。 */
    private String lastAttemptTime;

    /** Replay 来源投递 ID；原始投递为空。 */
    private String replayOfDeliveryId;

    /** UTC 创建时间。 */
    private String createTime;
}
