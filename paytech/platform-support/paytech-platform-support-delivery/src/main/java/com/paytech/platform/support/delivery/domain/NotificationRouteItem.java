package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板路由，账号以编码表示。 */
@Getter
@AllArgsConstructor
public class NotificationRouteItem {

    /** 主账号编码。 */
    private final String primaryProviderCode;

    /** 备用账号编码，可空。 */
    private final String fallbackProviderCode;

    /** 路由启停状态。 */
    private final NotificationConfigurationStatus status;

    /** 路由版本。 */
    private final int version;

    /** 路由更新时间。 */
    private final Instant updateTime;
}
