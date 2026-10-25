package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 资格决定来源（t_qualification_decision.decision_source）。 */
public enum QualificationDecisionSource implements CodeEnum {
    /** 规则评估自动形成。 */
    AUTOMATED("AUTOMATED"),

    /** 入驻审核人通过或拒绝。 */
    REVIEWER("REVIEWER"),

    /** 审核人覆盖规则建议。 */
    OVERRIDE("OVERRIDE"),

    /** 运营在资格登记页手工登记或撤销；不关联尽调案件。 */
    MANUAL("MANUAL");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    QualificationDecisionSource(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
