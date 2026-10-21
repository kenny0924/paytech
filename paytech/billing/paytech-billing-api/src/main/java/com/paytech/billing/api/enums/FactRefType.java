package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 计费事实引用的外部业务对象类型（t_billing_fact_ref.ref_type）
 * <li>一事实每种类型至多一条；每种事实类型必填的引用集合由 billing 应用层目录约束，新增事实类型只增加代码。</li>
 * <li>refId 按类型解释：数字 ID 存规范十进制字符串；各常量注释写明 Owner，billing 不建立跨库外键也不向 Owner 核对。</li>
 */
public enum FactRefType implements CodeEnum {
    /** 收单订单；Owner acquiring */
    ORDER,

    /** 收单订单上下文；Owner acquiring */
    ORDER_CONTEXT,

    /** 支付意图；Owner payment */
    PAYMENT_INTENT,

    /** 支付尝试；Owner payment */
    PAYMENT_ATTEMPT,

    /** 渠道技术 API 账户（t_channel_account）；Owner channel-connector */
    PAYMENT_CHANNEL_ACCOUNT,

    /** 渠道商户号（t_channel_mid）；Owner channel-connector */
    MID,

    /** 退款申请；Owner acquiring */
    REFUND_REQUEST,

    /** 渠道退款；Owner payment */
    CHANNEL_REFUND,

    /** 重复扣款明细；Owner payment */
    DUPLICATE_ITEM,

    /** 渠道争议通知；Owner payment */
    DISPUTE_NOTICE,

    /** 争议案件；Owner dispute */
    DISPUTE_CASE,

    /** 税额计算快照；Owner tax */
    TAX_SNAPSHOT,

    /** Finance 渠道清算账户，ledger 渠道账户 Owner 的来源；Owner reconciliation */
    CLEARING_ACCOUNT,

    /** 渠道结算批次；Owner reconciliation */
    SETTLEMENT_BATCH,

    /** 渠道结算明细；Owner reconciliation */
    SETTLEMENT_ITEM,

    /** 平台银行账户；Owner reconciliation */
    CASH_ACCOUNT,

    /** 出款单；Owner payout */
    PAYOUT,

    /** 缴税单；Owner tax */
    TAX_PAYMENT,

    /** 人工调整工单号；Owner 调用方工单（Operations 录入），billing 不核对 */
    TICKET,

    /** 外汇成交；Owner fx */
    FX_DEAL,

    /** 外汇报价；Owner fx */
    FX_QUOTE,

    /**
     * Payment 渠道交易引用（内部 ID）；Owner payment
     * <li>引用行同时带 externalReferenceType 与 externalTransactionId，reconciliation 按它们匹配渠道账单行；只由自动事实登记，人工登记不允许。</li>
     */
    CHANNEL_TRANSACTION_REF,

    /** 对账异常处置（RECON_ADJUSTMENT 的来源）；Owner reconciliation；只由自动事实登记，人工登记不允许 */
    RECON_RESOLUTION;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
