package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.enums.NotificationAuditField;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次配置变更中单个字段的前后值；正文只记录摘要。 */
@Getter
@AllArgsConstructor
public class NotificationFieldChange {

    /** 变更字段。 */
    private final NotificationAuditField field;

    /** 原值，可空。 */
    private final String before;

    /** 新值，可空。 */
    private final String after;
}
