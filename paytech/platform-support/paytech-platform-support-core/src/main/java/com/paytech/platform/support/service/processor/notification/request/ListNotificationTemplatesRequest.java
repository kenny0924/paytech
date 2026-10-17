package com.paytech.platform.support.service.processor.notification.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板分页查询。 */
@Getter
@AllArgsConstructor
public class ListNotificationTemplatesRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 已校验的分页。 */
    private final PagingRequestBody paging;

    /** 已转义的编码 LIKE 模式，可空。 */
    private final String keyword;

    /** 状态筛选，可空。 */
    private final NotificationConfigurationStatus status;
}
