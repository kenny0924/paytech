package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.enums.WebhookAuditField;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个审计字段的写入前后值；新建时 before 为空。 */
@Getter
@AllArgsConstructor
public class WebhookFieldChange {

    /** 受控审计字段。 */
    private final WebhookAuditField field;

    /** 写入前的值。 */
    private final String before;

    /** 写入后的值。 */
    private final String after;
}
