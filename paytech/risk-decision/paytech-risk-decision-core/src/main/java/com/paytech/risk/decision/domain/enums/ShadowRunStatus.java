package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 影子运行批次状态。 */
public enum ShadowRunStatus implements CodeEnum {
    /** 已创建，尚未开始回放。 */
    CREATED("CREATED"),

    /** 正在回放样本。 */
    RUNNING("RUNNING"),

    /** 全部样本已比较并完成汇总。 */
    COMPLETED("COMPLETED"),

    /** 批次异常结束。 */
    FAILED("FAILED"),

    /** 已取消。 */
    CANCELLED("CANCELLED");

    private final String code;

    ShadowRunStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
