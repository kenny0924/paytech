package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;

import lombok.Getter;
import lombok.Setter;

/** Webhook 签名密钥的 Vault 引用与宽限；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookSigningSecret {

    /** 签名密钥主键。 */
    private Long signingSecretId;

    /** 所属端点。 */
    private Long endpointId;

    /** 端点内第几把，从 1 开始。 */
    private int rotationNo;

    /** WEBHOOK_SIGNING_SECRET_V1 的 Vault 引用，禁止日志输出。 */
    private String secretVaultToken;

    /** 密钥状态。 */
    private WebhookSecretStatus status;

    /** ACTIVE 时为 1，否则为空。 */
    private Integer signingSlot;

    /**
     * RETIRED 后仍参与签名的截止时间
     * <li>ACTIVE 时为空。</li>
     */
    private Instant validUntil;

    /** 条件更新版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;

    /** 在给定时刻仍可参与签名：ACTIVE，或 RETIRED 且未过宽限截止时间。 */
    public boolean canSignAt(Instant now) {
        return status == WebhookSecretStatus.ACTIVE || validUntil != null && validUntil.isAfter(now);
    }
}
