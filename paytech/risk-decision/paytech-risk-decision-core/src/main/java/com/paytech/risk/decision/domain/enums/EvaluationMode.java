package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 决策评估模式。 */
public enum EvaluationMode implements CodeEnum {
    /** 正式评估，结论返回调用方并写入累计事件。 */
    PRIMARY("PRIMARY"),

    /** 影子运行，候选发布回放基线评估的输入，不影响正式结论。 */
    SHADOW("SHADOW"),

    /** 按原输入重放，供排查。 */
    REPLAY("REPLAY");

    private final String code;

    EvaluationMode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
