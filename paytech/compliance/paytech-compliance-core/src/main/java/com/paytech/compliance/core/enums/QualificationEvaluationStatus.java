package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 查询时实时判定的角色资格状态
 * <li>与 compliance-api 发布的同名 Code 一致；只有 ELIGIBLE 表示合格。</li>
 */
public enum QualificationEvaluationStatus implements CodeEnum {
    /** 已批准且在有效期内；Client 另需没有生效中的结算冻结。 */
    ELIGIBLE("ELIGIBLE"),

    /** 没有该角色要求类型的资格行。 */
    NOT_REGISTERED("NOT_REGISTERED"),

    /** 当前结论为拒绝。 */
    REJECTED("REJECTED"),

    /** 当前结论为撤销。 */
    REVOKED("REVOKED"),

    /** 已过期：结论为 EXPIRED，或已批准但有效结束时间不晚于查询时间。 */
    EXPIRED("EXPIRED"),

    /** 已批准但有效开始时间晚于查询时间。 */
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
}
