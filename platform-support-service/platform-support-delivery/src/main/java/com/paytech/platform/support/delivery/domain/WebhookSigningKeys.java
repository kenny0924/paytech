package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;
import com.paytech.platform.support.delivery.core.enums.WebhookSecretStatus;

/** 从一个端点的密钥中选出参与签名的密钥：一把 ACTIVE，加上宽限期内最新的一把 RETIRED。 */
public final class WebhookSigningKeys {

    /** 只承载静态方法。 */
    private WebhookSigningKeys() {
    }

    /** 当前 ACTIVE 密钥；缺失说明端点数据被破坏。 */
    public static WebhookSigningSecret active(List<WebhookSigningSecret> secrets) {
        return secrets.stream().filter(secret -> secret.getStatus() == WebhookSecretStatus.ACTIVE).findFirst()
            .orElseThrow(() -> new ErrorException("Webhook 端点缺少 ACTIVE 签名密钥"));
    }

    /** 在给定时刻仍处于宽限期的最新 RETIRED 密钥；没有时为空。 */
    public static WebhookSigningSecret grace(List<WebhookSigningSecret> secrets, Instant now) {
        return secrets.stream().filter(secret -> secret.getStatus() == WebhookSecretStatus.RETIRED)
            .filter(secret -> secret.canSignAt(now)).max(Comparator.comparingInt(WebhookSigningSecret::getRotationNo))
            .orElse(null);
    }
}
