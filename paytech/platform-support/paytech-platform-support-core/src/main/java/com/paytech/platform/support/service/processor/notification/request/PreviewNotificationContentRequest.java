package com.paytech.platform.support.service.processor.notification.request;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预览内容草稿。 */
@Getter
@AllArgsConstructor
public class PreviewNotificationContentRequest {

    /** 可信 Operations 账号标识。 */
    private final String actor;

    /** 当前普通 Operations 会话。 */
    private final String session;

    /** 标题模板草稿。 */
    private final String subjectTemplate;

    /** 正文模板草稿。 */
    private final String bodyTemplate;

    /** 示例参数，非空。 */
    private final Map<String, String> parameters;
}
