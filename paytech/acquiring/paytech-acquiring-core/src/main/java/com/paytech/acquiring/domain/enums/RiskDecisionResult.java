package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk 统一评估返回的结论；未知 Code 由调用方按上游协议错误失败关闭。 */
public enum RiskDecisionResult implements CodeEnum {
    /** 放行，Commerce 冻结评估 ID 后继续创建。 */
    ALLOW("ALLOW"),

    /** 需要审核；结账不支持人工流程，按拒绝处理。 */
    REVIEW("REVIEW"),

    /** 拒绝。 */
    DENY("DENY"),

    /** 执行失败，已由 Risk 落库；重放结果相同，失败关闭。 */
    FAILED("FAILED"),

    /** 执行超时，已由 Risk 落库；重放结果相同，失败关闭。 */
    TIMED_OUT("TIMED_OUT");

    private final String code;

    RiskDecisionResult(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
