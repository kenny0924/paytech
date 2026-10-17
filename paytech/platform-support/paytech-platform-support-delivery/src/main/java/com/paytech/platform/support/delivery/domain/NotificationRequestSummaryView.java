package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.Map;

import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发送记录列表项，组合请求、模板与投递三表。 */
@Getter
@AllArgsConstructor
public class NotificationRequestSummaryView {

    /** 请求身份。 */
    private final long requestId;

    /** 来源系统。 */
    private final String sourceSystem;

    /** 来源事件键。 */
    private final String sourceEventKey;

    /** 模板编码；模板已不存在时为空。 */
    private final String templateCode;

    /** 租户，平台通知为空。 */
    private final Long tenantId;

    /** 默认语言。 */
    private final String defaultLocale;

    /** 收件人数。 */
    private final int deliveryCount;

    /** 各投递状态的数量。 */
    private final Map<NotificationDeliveryStatus, Integer> statusCounts;

    /** 受理时间。 */
    private final Instant createTime;
}
