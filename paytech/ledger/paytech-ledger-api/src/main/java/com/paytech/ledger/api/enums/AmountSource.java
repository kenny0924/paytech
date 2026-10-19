package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 自动记账规则行金额来源（规则行 amount_source）
 * <li>GROSS_AMOUNT 取 billing 计费事实头的 grossAmount；其余按同名 billing {@code FactComponentType} 明细行汇总 postedAmount。</li>
 */
public enum AmountSource implements CodeEnum {
    /** 计费事实头总额 */
    GROSS_AMOUNT,

    /** Client 本金 */
    CLIENT_PRINCIPAL,

    /** Client 手续费 */
    CLIENT_FEE,

    /** Seller 实收税 */
    SELLER_TAX,

    /** 预计渠道成本 */
    CHANNEL_COST,

    /** Local Partner 分润 */
    PARTNER_SHARE,

    /** 平台服务费 */
    PLATFORM_SERVICE_CHARGE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
