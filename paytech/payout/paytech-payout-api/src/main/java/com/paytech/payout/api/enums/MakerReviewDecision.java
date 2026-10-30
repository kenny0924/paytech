package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 核验意见人（Maker）的意见（review_maker_decision）
 */
public enum MakerReviewDecision implements CodeEnum {
    /** 建议通过 */
    APPROVE,

    /** 建议拒绝 */
    REJECT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
