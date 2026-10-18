package com.paytech.platform.support.service.processor.webhook.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改端点地址与订阅。 */
@Getter
@AllArgsConstructor
public class UpdateWebhookEndpointRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 端点 ID。 */
    private final long endpointId;

    /** 请求地址，尚未做 DNS 校验。 */
    private final String url;

    /** 已校验的订阅事件类型。 */
    private final List<String> eventTypes;

    /** 调用方看到的版本。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
