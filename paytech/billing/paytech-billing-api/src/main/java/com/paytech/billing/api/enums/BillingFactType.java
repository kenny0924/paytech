package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计费事实类型（t_billing_fact.fact_type）；account 余额映射与 ledger 规则行共享同一稳定代码。 */
public enum BillingFactType implements CodeEnum {
    /** 支付成功：毛额、Client 手续费、税、分润、平台服务费与预计渠道成本 */
    PAYMENT,

    /** 退款成功：按原支付事实比例冲回 */
    REFUND,

    /** 重复扣款：确认消费者被多扣的金额，应退消费者 */
    DUPLICATE_CAPTURE,

    /** 重复扣款补偿：向消费者退还重复扣款 */
    DUPLICATE_COMPENSATION,

    /** 争议结论：本金与争议费用的承担 */
    DISPUTE,

    /** 渠道成本调整：实际成本与原支付事实预计成本的差额 */
    COST_ADJUSTMENT,

    /** 渠道结算：银行确认的渠道清算款到账 */
    CHANNEL_SETTLEMENT,

    /** 出款完成：银行确认的出款金额与出款手续费 */
    PAYOUT_SETTLED,

    /** 缴税完成：银行确认的税款缴纳 */
    TAX_PAID,

    /** 影响 Owner 余额的人工调整（Maker-Checker 登记） */
    MANUAL_ADJUSTMENT,

    /** 外汇兑换卖出币种一侧 */
    FX_SELL,

    /** 外汇兑换买入币种一侧 */
    FX_BUY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
