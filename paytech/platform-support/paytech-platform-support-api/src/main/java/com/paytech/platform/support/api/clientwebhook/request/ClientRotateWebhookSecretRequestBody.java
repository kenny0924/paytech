package com.paytech.platform.support.api.clientwebhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户轮换签名密钥。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientRotateWebhookSecretRequestBody {

    /** false：旧密钥再参与签名 1 小时；true：旧密钥立即失效。 */
    private boolean immediate;
}
