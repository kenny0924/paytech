package com.paytech.platform.support.service.processor.webhook.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建端点并生成首个签名密钥。 */
@Getter
@AllArgsConstructor
public class CreateWebhookEndpointRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** CCT Client ID。 */
    private final long clientId;

    /** 请求地址，尚未做 DNS 校验。 */
    private final String url;

    /** 已校验的订阅事件类型。 */
    private final List<String> eventTypes;

    /** 操作原因。 */
    private final String reason;
}
