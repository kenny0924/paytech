package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 通知领域持久化事实；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class NotificationTemplateContent {

    /** 语言内容标识。 */
    private Long contentId;

    /** 所属模板定义。 */
    private Long templateId;

    /** 规范 BCP 47 语言标记。 */
    private String locale;

    /** 邮件标题，采用 ${parameter} 占位符。 */
    private String subjectTemplate;

    /** 纯文本正文，采用 ${parameter} 占位符。 */
    private String bodyTemplate;

    /** 当前文案的并发版本。 */
    private int version;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}
