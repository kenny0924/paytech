package com.paytech.platform.support.delivery.domain;

import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookDelivery;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookEvent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次事件受理要原子保存的事件及其投递。 */
@Getter
@AllArgsConstructor
public class AcceptWebhookEventContext {

    /** 新事件。 */
    private final WebhookEvent event;

    /** 按当前 ACTIVE 订阅生成的投递；可为空列表。 */
    private final List<WebhookDelivery> deliveries;
}
