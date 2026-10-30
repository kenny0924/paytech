package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 余额解释中"当前不能提款"的原因（technique 7.4）
 * <li>本期只判定前四项；CAPABILITY_NOT_ENABLED、SETTLEMENT_CONTROLLED 为 S2 预留，本期不返回。</li>
 */
public enum BeneficiaryBlockReason implements CodeEnum {
    /** 该 OE × 币种没有可用余额 */
    NO_AVAILABLE_BALANCE,

    /** 该币种没有核验通过的收款账户 */
    NO_USABLE_ACCOUNT,

    /** 该币种的收款账户都在冷却期 */
    ACCOUNT_COOLING,

    /** 存在未清追偿应收 */
    RECOVERY_OUTSTANDING,

    /** 出款开通项未生效（S2 预留） */
    CAPABILITY_NOT_ENABLED,

    /** 处于结算控制（S2 预留） */
    SETTLEMENT_CONTROLLED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
