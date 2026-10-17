package com.paytech.platform.support.api.notificationmanagement.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 模板某一语言的内容。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateContentItem {

    /** 语言。 */
    private String locale;

    /** 标题模板。 */
    private String subjectTemplate;

    /** 正文模板。 */
    private String bodyTemplate;

    /** 内容版本。 */
    private int version;

    /** 更新时间，UTC。 */
    private Instant updateTime;
}
