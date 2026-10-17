package com.paytech.platform.support.delivery.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次配置写入的结果：对象编码、前后版本与字段差异。 */
@Getter
@AllArgsConstructor
public class NotificationConfigurationChange {

    /** 对象编码：模板编码、语言或账号编码。 */
    private final String code;

    /** 写入前版本；新建时为 0。 */
    private final int beforeVersion;

    /** 写入后版本。 */
    private final int afterVersion;

    /** 字段差异，供调用方记录审计。 */
    private final List<NotificationFieldChange> changes;
}
