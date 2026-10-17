package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对已结束的投递生成新投递。 */
@Getter
@AllArgsConstructor
public class ReplayWebhookDeliveryCommand {

    /** 原投递 ID。 */
    private final long deliveryId;

    /** 操作人 Operations 账号。 */
    private final long requestedBy;

    /** 写入时间。 */
    private final Instant now;
}
