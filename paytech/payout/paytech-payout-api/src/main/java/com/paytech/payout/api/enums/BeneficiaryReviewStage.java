package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 核验队列阶段
 */
public enum BeneficiaryReviewStage implements CodeEnum {
    /** 待 Maker 出具意见 */
    MAKER,

    /** 已有 Maker 意见、待 Checker 决定 */
    CHECKER,

    /** 近 30 天已有复核决定 */
    DONE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
