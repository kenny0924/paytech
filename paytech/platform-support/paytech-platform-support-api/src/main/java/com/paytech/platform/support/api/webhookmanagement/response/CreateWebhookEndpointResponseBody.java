package com.paytech.platform.support.api.webhookmanagement.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新建端点结果；签名密钥明文只在本响应中出现一次。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookEndpointResponseBody {

    /** 新端点 ID，字符串形式。 */
    private String endpointId;

    /** 端点版本，固定为 1。 */
    private int version;

    /**
     * 首个签名密钥明文
     * <li>格式 whsec_ 加 43 位 Base64URL；之后无法再查看，须通过安全渠道交给 Client。</li>
     */
    private String signingSecret;
}
