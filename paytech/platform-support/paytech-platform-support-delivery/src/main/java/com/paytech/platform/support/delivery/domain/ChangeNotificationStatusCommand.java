package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启停模板、路由或发送账号。 */
@Getter
@AllArgsConstructor
public class ChangeNotificationStatusCommand {

    /** 模板编码或账号编码。 */
    private final String code;

    /** 目标状态。 */
    private final NotificationConfigurationStatus status;

    /** 调用方看到的当前版本。 */
    private final int expectedVersion;

    /** 本次写入时间。 */
    private final Instant now;
}
