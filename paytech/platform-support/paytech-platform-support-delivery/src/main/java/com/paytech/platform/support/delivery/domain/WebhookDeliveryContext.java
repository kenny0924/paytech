package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEndpoint;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次投递执行读取的事实；按读取时刻判断可签名密钥。 */
@Getter
@AllArgsConstructor
public class WebhookDeliveryContext {

    /** 投递。 */
    private final WebhookDelivery delivery;

    /** 所属事件。 */
    private final WebhookEvent event;

    /** 目标端点。 */
    private final WebhookEndpoint endpoint;

    /** 端点的全部密钥。 */
    private final List<WebhookSigningSecret> secrets;

    /** 上次执行遗留的 PROCESSING 尝试；没有时为空。 */
    private final WebhookAttempt processingAttempt;

    /** 端点仍启用且仍订阅该事件类型。 */
    public boolean deliverable() {
        return endpoint.accepts(delivery.getEventType());
    }

    /** 当前 ACTIVE 密钥。 */
    public WebhookSigningSecret activeSecret() {
        return WebhookSigningKeys.active(secrets);
    }

    /** 在给定时刻仍处于宽限期的密钥；没有时为空。 */
    public WebhookSigningSecret graceSecret(Instant now) {
        return WebhookSigningKeys.grace(secrets, now);
    }
}
