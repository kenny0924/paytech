package com.paytech.platform.support.integration.domain.verify;

/** 输出期望的匹配方式。 */
public enum VerifyMatch {

    /** 与期望值 JSON 相等。 */
    EQUALS,

    /** 存在且不为 null。 */
    EXISTS,

    /** 不存在或为 null。 */
    ABSENT,

    /** 字符串值中能找到正则匹配。 */
    REGEX,

    /** 等于期望数组中的某一个值。 */
    ONE_OF
}
