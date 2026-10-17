package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationRoute {

    /** 路由标识。 */
    private Long routeId;

    /** 唯一所属模板定义。 */
    private Long notificationTemplateId;

    /** 当前首选发送账号。 */
    private Long primaryProviderId;

    /** 可空备用发送账号。 */
    private Long fallbackProviderId;

    /** 当前路由启停状态。 */
    private NotificationConfigurationStatus status;

    /** 当前主备配置的并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
