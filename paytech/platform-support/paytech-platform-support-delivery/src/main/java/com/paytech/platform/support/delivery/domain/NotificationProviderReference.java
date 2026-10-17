package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 引用发送账号的路由。 */
@Getter
@AllArgsConstructor
public class NotificationProviderReference {

    /** 路由所属模板编码。 */
    private final String templateCode;

    /** PRIMARY 或 FALLBACK。 */
    private final String role;

    /** 路由启停状态。 */
    private final NotificationConfigurationStatus routeStatus;
}
