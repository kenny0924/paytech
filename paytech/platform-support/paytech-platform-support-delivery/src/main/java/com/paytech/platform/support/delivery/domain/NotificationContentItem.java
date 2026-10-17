package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板某一语言的内容。 */
@Getter
@AllArgsConstructor
public class NotificationContentItem {

    /** 语言。 */
    private final String locale;

    /** 标题模板。 */
    private final String subjectTemplate;

    /** 正文模板。 */
    private final String bodyTemplate;

    /** 内容版本。 */
    private final int version;

    /** 内容更新时间。 */
    private final Instant updateTime;
}
