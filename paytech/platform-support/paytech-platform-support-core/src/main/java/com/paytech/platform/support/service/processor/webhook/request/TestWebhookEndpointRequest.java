package com.paytech.platform.support.service.processor.webhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 同步发送测试事件。 */
@Getter
@AllArgsConstructor
public class TestWebhookEndpointRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 端点 ID。 */
    private final long endpointId;
}
