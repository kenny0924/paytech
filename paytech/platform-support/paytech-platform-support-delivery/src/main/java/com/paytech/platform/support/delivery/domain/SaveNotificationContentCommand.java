package com.paytech.platform.support.delivery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建或修改模板某一语言的内容。 */
@Getter
@AllArgsConstructor
public class SaveNotificationContentCommand {

    /** 模板编码。 */
    private final String templateCode;

    /** 规范化后的语言。 */
    private final String locale;

    /** 标题模板。 */
    private final String subjectTemplate;

    /** 正文模板。 */
    private final String bodyTemplate;

    /** 当前版本；0 表示该语言尚不存在、新建。 */
    private final int expectedVersion;

    /** 本次写入时间。 */
    private final Instant now;
}
