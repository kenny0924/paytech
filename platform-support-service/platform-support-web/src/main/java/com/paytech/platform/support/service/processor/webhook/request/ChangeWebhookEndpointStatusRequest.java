package com.paytech.platform.support.service.processor.webhook.request;

import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启用或停用端点。 */
@Getter
@AllArgsConstructor
public class ChangeWebhookEndpointStatusRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 端点 ID。 */
    private final long endpointId;

    /** 目标状态。 */
    private final WebhookEndpointStatus status;

    /** 调用方看到的版本。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
