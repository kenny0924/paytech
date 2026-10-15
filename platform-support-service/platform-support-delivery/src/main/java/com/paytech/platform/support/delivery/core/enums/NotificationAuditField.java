package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通知配置审计记录的字段；正文类字段只记录 SHA-256 摘要。 */
@Getter
@RequiredArgsConstructor
public enum NotificationAuditField implements CodeEnum {

    CODE("CODE"),

    STATUS("STATUS"),

    LOCALE("LOCALE"),

    SUBJECT_DIGEST("SUBJECT_DIGEST"),

    BODY_DIGEST("BODY_DIGEST"),

    PRIMARY_PROVIDER("PRIMARY_PROVIDER"),

    FALLBACK_PROVIDER("FALLBACK_PROVIDER");

    private final String code;
}
