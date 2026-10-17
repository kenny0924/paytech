package com.paytech.platform.support.delivery.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 内容模板的字段问题，供入口转换为字段错误。 */
@Getter
@AllArgsConstructor
public class NotificationTemplateIssue {

    /** 请求字段名：subjectTemplate 或 bodyTemplate。 */
    private final String field;

    /** 可外显的中文说明。 */
    private final String message;
}
