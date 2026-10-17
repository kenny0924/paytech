package com.paytech.platform.support.service.processor.notification.request;

import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发送账号分页查询。 */
@Getter
@AllArgsConstructor
public class ListNotificationProvidersRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 已校验的分页。 */
    private final PagingRequestBody paging;
}
