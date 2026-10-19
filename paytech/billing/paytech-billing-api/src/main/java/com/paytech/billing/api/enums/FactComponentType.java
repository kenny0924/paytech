package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 计费事实明细行的金额组成类型（t_billing_fact_line.component_type）
 * <li>account 按付方 / 收方与组件类型推导 Owner 账户借贷，ledger 规则行的金额来源按同名代码汇总 postedAmount。</li>
 * <li>成本调整用 CHANNEL_COST + ADJUSTMENT，增提或冲减由付方 / 收方方向表达。</li>
 */
public enum FactComponentType implements CodeEnum {
    /** Client 本金（应付 Client 的交易款） */
    CLIENT_PRINCIPAL,

    /** Client 手续费 */
    CLIENT_FEE,

    /** Seller 实收税 */
    SELLER_TAX,

    /** Local Partner 分润 */
    PARTNER_SHARE,

    /** 平台服务费（Partner OE 付给平台 Finance Entity） */
    PLATFORM_SERVICE_CHARGE,

    /** 预计渠道成本或其调整 */
    CHANNEL_COST,

    /** 渠道结算时实际扣除的渠道成本 */
    CHANNEL_COST_SETTLED,

    /** 重复扣款金额 */
    DUPLICATE_AMOUNT,

    /** 出款手续费 */
    PAYOUT_FEE,

    /** 出款金额 */
    PAYOUT_AMOUNT,

    /** 渠道结算到账金额 */
    SETTLEMENT_AMOUNT,

    /** 缴税金额 */
    TAX_PAYMENT_AMOUNT,

    /** 外汇兑换卖出金额 */
    FX_SOLD_AMOUNT,

    /** 外汇兑换买入金额 */
    FX_BOUGHT_AMOUNT,

    /** 外汇兑换费用 */
    FX_FEE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
