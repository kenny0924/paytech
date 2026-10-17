package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个收件人的投递，不含邮箱与参数值。 */
@Getter
@AllArgsConstructor
public class NotificationDeliveryItem {

    /** 投递身份。 */
    private final long deliveryId;

    /** 收件人语言；为空表示继承请求默认语言。 */
    private final String locale;

    /** 收件人覆盖参数的名称，升序。 */
    private final List<String> parameterKeys;

    /** 投递状态。 */
    private final NotificationDeliveryStatus status;

    /** 已创建尝试数。 */
    private final int attemptCount;

    /** 下次重试时间，可空。 */
    private final Instant nextRetryTime;

    /** 投递更新时间。 */
    private final Instant updateTime;

    /** 尝试，按创建先后。 */
    private final List<NotificationAttemptItem> attempts;
}
