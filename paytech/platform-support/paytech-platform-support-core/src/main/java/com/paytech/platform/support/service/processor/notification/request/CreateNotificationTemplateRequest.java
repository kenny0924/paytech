package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建模板。 */
@Getter
@AllArgsConstructor
public class CreateNotificationTemplateRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 模板编码。 */
    private final String templateCode;

    /** 操作原因。 */
    private final String reason;
}
