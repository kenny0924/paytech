package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 筛查命中处置结论（t_screening_resolution.resolution，design 6.1 SM-I）。 */
public enum ScreeningHitResolution implements CodeEnum {
    NO_MATCH("NO_MATCH", true),

    FALSE_POSITIVE("FALSE_POSITIVE", true),

    POTENTIAL_MATCH("POTENTIAL_MATCH", false),

    TRUE_POSITIVE_ACCEPT("TRUE_POSITIVE_ACCEPT", true),

    TRUE_POSITIVE_REJECT("TRUE_POSITIVE_REJECT", true),

    INSUFFICIENT_EVIDENCE("INSUFFICIENT_EVIDENCE", false);

    private final String code;

    /** 该处置是否关闭命中（OPEN → RESOLVED）。 */
    private final boolean closesHit;

    /** 固定业务代码与是否关闭命中。 */
    ScreeningHitResolution(String code, boolean closesHit) {
        this.code = code;
        this.closesHit = closesHit;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 该处置是否关闭命中。 */
    public boolean closesHit() {
        return closesHit;
    }

    /** 按代码查找，未知代码返回 null。 */
    public static ScreeningHitResolution ofCode(String code) {
        for (ScreeningHitResolution value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
