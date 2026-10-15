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

    FALLBACK_PROVIDER("FALLBACK_PROVIDER"),

    PROVIDER_TYPE("PROVIDER_TYPE"),

    REGION("REGION"),

    FROM_ADDRESS("FROM_ADDRESS"),

    CONFIGURATION_SET("CONFIGURATION_SET"),

    /** 只记录 SET / REPLACED，不记录 Token 或密钥。 */
    CREDENTIAL("CREDENTIAL"),

    /** 测试发送的有界结果码。 */
    TEST_RESULT("TEST_RESULT"),

    /** 测试收件邮箱的 SHA-256 摘要。 */
    TEST_RECIPIENT_DIGEST("TEST_RECIPIENT_DIGEST");

    private final String code;
}
