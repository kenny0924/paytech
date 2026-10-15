package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Replay 结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplayWebhookDeliveryResponseBody {

    /** 新投递 ID，字符串形式。 */
    private String deliveryId;
}
