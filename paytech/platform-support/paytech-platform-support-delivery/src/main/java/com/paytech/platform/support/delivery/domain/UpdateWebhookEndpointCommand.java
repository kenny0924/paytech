package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改端点地址与订阅；地址与订阅已校验。 */
@Getter
@AllArgsConstructor
public class UpdateWebhookEndpointCommand {

    /** 端点 ID。 */
    private final long endpointId;

    /** 已校验的 HTTPS 地址。 */
    private final String url;

    /** 已校验的订阅事件类型。 */
    private final List<String> eventTypes;

    /** 调用方看到的版本。 */
    private final int expectedVersion;

    /** 写入时间。 */
    private final Instant now;
}
