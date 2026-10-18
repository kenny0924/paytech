package com.paytech.platform.support.delivery.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationMedium;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板详情，组合模板、全部语言内容与路由。 */
@Getter
@AllArgsConstructor
public class NotificationTemplateDetailView {

    /** 模板编码。 */
    private final String templateCode;

    /** 介质。 */
    private final NotificationMedium medium;

    /** 模板启停状态。 */
    private final NotificationConfigurationStatus status;

    /** 是否只能即时发送（认证邮件）。 */
    private final boolean transientTemplate;

    /** 正文必须包含的占位符名称；普通模板为空。 */
    private final List<String> requiredParameters;

    /** 模板版本。 */
    private final int version;

    /** 模板更新时间。 */
    private final Instant updateTime;

    /** 全部语言内容，按语言升序。 */
    private final List<NotificationContentItem> contents;

    /** 路由；未配置时为空。 */
    private final NotificationRouteItem route;
}
