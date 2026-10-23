package com.paytech.billing.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * 登记一条自动 PAYMENT 事实所需的全部已验证输入：订单支付成功事实与事务外读取的上游事实、币种精度与到期转可用时间
 * <li>由 RecordPaymentFactProcessor 在交叉校验通过后一次构造，交给 PaymentFactAssembler 做纯内存组装；不可变。</li>
 * <li>无 Local Partner 时 partnerSharePrice、serviceChargePrice、financeEntityId 均为空。</li>
 */
@Getter
@Builder
public class PaymentFactInputs {
    /** 订单支付成功事实（含订单冻结商业字段） */
    private final OrderPaymentSucceededFact payment;

    /** Client 定价命中价格 */
    private final CommercialPrice clientPrice;

    /** Partner 分润命中价格 */
    private final CommercialPrice partnerSharePrice;

    /** 平台服务费命中价格 */
    private final CommercialPrice serviceChargePrice;

    /** 平台服务费收取方 Finance Entity */
    private final Long financeEntityId;

    /** 渠道成本命中结果 */
    private final ChannelCostResolution channelCost;

    /** 清算账户 */
    private final ClearingAccount clearingAccount;

    /** 事实币种精度 */
    private final int minorUnit;

    /** 到期转可用时间（UTC） */
    private final Instant balanceAvailableTime;
}
