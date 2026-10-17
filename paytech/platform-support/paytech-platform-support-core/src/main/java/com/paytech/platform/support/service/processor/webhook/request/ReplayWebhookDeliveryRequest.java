package com.paytech.platform.support.service.processor.webhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对已结束的投递生成新投递。 */
@Getter
@AllArgsConstructor
public class ReplayWebhookDeliveryRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 原投递 ID。 */
    private final long deliveryId;

    /** 操作原因。 */
    private final String reason;
}
