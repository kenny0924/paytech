package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发送账号列表项，组合账号、路由与模板三表。 */
@Getter
@AllArgsConstructor
public class NotificationProviderView {

    /** 账号编码。 */
    private final String providerCode;

    /** 介质。 */
    private final NotificationMedium medium;

    /** 账号启停状态。 */
    private final NotificationConfigurationStatus status;

    /** 本环境是否有唯一匹配的发送适配器；未开启发送时为 false。 */
    private final boolean adapterConfigured;

    /** 账号版本。 */
    private final int version;

    /** 账号更新时间。 */
    private final Instant updateTime;

    /** 引用该账号的路由，按路由创建顺序。 */
    private final List<NotificationProviderReference> references;
}
