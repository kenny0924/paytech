package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 轮换结果；新密钥明文只在本响应中出现一次。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotateWebhookSecretResponseBody {

    /** 新密钥是第几把。 */
    private int rotationNo;

    /**
     * 新签名密钥明文
     * <li>格式 whsec_ 加 43 位 Base64URL；之后无法再查看。</li>
     */
    private String signingSecret;

    /**
     * 旧密钥宽限截止时间
     * <li>UTC；立即作废时为空。</li>
     */
    private String previousValidUntil;
}
