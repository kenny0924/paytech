package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启用或停用端点。 */
@Getter
@AllArgsConstructor
public class ChangeWebhookEndpointStatusCommand {

    /** 端点 ID。 */
    private final long endpointId;

    /** 目标状态。 */
    private final WebhookEndpointStatus status;

    /** 调用方看到的版本。 */
    private final int expectedVersion;

    /** 写入时间。 */
    private final Instant now;
}
