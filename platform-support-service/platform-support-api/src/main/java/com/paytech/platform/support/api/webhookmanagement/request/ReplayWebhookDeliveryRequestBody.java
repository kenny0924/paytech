package com.paytech.platform.support.api.webhookmanagement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 重新投递一条已结束的 Webhook 投递。 */
@Getter
@Setter
public class ReplayWebhookDeliveryRequestBody {

    /**
     * 操作原因
     * <li>1..500 字符，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
