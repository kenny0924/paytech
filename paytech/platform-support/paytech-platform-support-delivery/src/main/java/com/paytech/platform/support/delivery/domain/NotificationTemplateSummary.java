package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板列表项，组合模板、语言内容与路由三表。 */
@Getter
@AllArgsConstructor
public class NotificationTemplateSummary {

    /** 模板编码。 */
    private final String templateCode;

    /** 介质。 */
    private final NotificationMedium medium;

    /** 模板启停状态。 */
    private final NotificationConfigurationStatus status;

    /** 已有语言，升序。 */
    private final List<String> locales;

    /** 是否只能即时发送（认证邮件）。 */
    private final boolean transientTemplate;

    /** 路由状态；未配置路由时为空。 */
    private final NotificationConfigurationStatus routeStatus;

    /** 模板版本。 */
    private final int version;

    /** 模板更新时间。 */
    private final Instant updateTime;
}
