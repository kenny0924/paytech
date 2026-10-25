package com.paytech.compliance.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 资格判定接口返回的实时状态；只有 ELIGIBLE 表示合格
 * <li>DTO 字段以 String 承载；消费方显式解析，未知 Code 按不合格处理。</li>
 */
public enum QualificationEvaluationStatus implements CodeEnum {
    /** 已批准且在有效期内；Client 另需没有生效中的结算冻结。 */
    ELIGIBLE("ELIGIBLE"),

    /** 没有该角色要求类型的资格。 */
    NOT_REGISTERED("NOT_REGISTERED"),

    /** 当前结论为拒绝。 */
    REJECTED("REJECTED"),

    /** 当前结论为撤销。 */
    REVOKED("REVOKED"),

    /** 已过期：结论为 EXPIRED，或已批准但有效结束时间不晚于判定时间。 */
    EXPIRED("EXPIRED"),

    /** 已批准但有效开始时间晚于判定时间。 */
    NOT_YET_VALID("NOT_YET_VALID"),

    /** 仅 Client：资格有效，但存在生效中的 SETTLEMENT_BLOCK 结算控制。 */
    SETTLEMENT_BLOCKED("SETTLEMENT_BLOCKED");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationEvaluationStatus(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static QualificationEvaluationStatus ofCode(String code) {
        for (QualificationEvaluationStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
