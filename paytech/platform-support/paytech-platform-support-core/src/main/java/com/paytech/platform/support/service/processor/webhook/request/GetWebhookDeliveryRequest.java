package com.paytech.platform.support.service.processor.webhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 读取投递详情。 */
@Getter
@AllArgsConstructor
public class GetWebhookDeliveryRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 投递 ID。 */
    private final long deliveryId;
}
