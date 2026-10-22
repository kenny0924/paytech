package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知规则类型。 */
@Getter
@RequiredArgsConstructor
public enum NotificationRuleType implements CodeEnum {

    /** 通知人员。 */
    NOTIFY("NOTIFY"),

    /** 发布业务回调事件。 */
    CALLBACK("CALLBACK");

    private final String code;
}
