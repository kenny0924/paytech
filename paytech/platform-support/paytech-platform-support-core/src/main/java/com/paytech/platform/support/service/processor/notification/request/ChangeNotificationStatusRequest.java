package com.paytech.platform.support.service.processor.notification.request;

import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启停模板、路由或发送账号。 */
@Getter
@AllArgsConstructor
public class ChangeNotificationStatusRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 模板编码或账号编码。 */
    private final String code;

    /** 目标状态。 */
    private final NotificationConfigurationStatus status;

    /** 调用方看到的当前版本。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
