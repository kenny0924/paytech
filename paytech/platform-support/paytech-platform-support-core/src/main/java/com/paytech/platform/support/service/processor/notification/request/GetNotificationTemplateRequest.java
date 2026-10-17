package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板详情查询。 */
@Getter
@AllArgsConstructor
public class GetNotificationTemplateRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 模板编码。 */
    private final String templateCode;
}
