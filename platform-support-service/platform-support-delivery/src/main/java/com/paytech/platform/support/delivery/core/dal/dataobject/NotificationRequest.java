package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationRequest {

    /** 通知请求标识。 */
    private Long requestId;

    /** 请求归属租户；平台通知为空。 */
    private Long tenantId;

    /** 服务端认可的来源系统。 */
    private String sourceSystem;

    /** 来源系统内永久唯一的通知意图。 */
    private String sourceEventKey;

    /** 模板定义标识。 */
    private Long notificationTemplateId;

    /** 收件人未覆盖时使用的语言。 */
    private String defaultLocale;

    /** 允许持久化的普通参数 JSON。 */
    private String defaultParameters;

    /** 受理时选定的路由标识。 */
    private Long notificationRouteId;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
