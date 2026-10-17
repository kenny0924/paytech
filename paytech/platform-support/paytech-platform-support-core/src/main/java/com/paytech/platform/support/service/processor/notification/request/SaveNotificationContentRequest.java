package com.paytech.platform.support.service.processor.notification.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或修改语言内容。 */
@Getter
@AllArgsConstructor
public class SaveNotificationContentRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 模板编码。 */
    private final String templateCode;

    /** 规范化后的语言。 */
    private final String locale;

    /** 标题模板。 */
    private final String subjectTemplate;

    /** 正文模板。 */
    private final String bodyTemplate;

    /** 当前版本，0 为新建。 */
    private final int expectedVersion;

    /** 操作原因。 */
    private final String reason;
}
