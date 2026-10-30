package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 运营查看原值的原因（Reveal 事件 reason_code）
 * <li>只区分核验阶段，用于与 Vault 侧审计互证；不是业务状态原因。</li>
 */
public enum BeneficiaryRevealReason implements CodeEnum {
    /** Maker 核验对照法定名 */
    MAKER_REVIEW,

    /** Checker 复核 */
    CHECKER_REVIEW;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
