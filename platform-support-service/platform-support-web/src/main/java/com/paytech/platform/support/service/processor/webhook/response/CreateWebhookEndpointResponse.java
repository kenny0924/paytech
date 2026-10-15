package com.paytech.platform.support.service.processor.webhook.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建端点结果；密钥明文只在本次响应中返回。 */
@Getter
@AllArgsConstructor
public class CreateWebhookEndpointResponse {

    /** 新端点 ID。 */
    private final long endpointId;

    /** 端点版本。 */
    private final int version;

    /** 首个签名密钥明文。 */
    private final String signingSecret;
}
