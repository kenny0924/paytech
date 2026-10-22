package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知渠道。 */
@Getter
@RequiredArgsConstructor
public enum NotificationChannel implements CodeEnum {

    /** 我的待办。 */
    INBOX("INBOX"),

    /** 邮件，经 Platform Delivery 发送。 */
    EMAIL("EMAIL");

    private final String code;
}
