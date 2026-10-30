package com.paytech.acquiring.api.checkout.v1.checkout.response;

import com.paytech.acquiring.api.checkout.v1.checkout.response.GetCheckoutResponseBody.CheckoutAmountsInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存结果不回显个人原值或保护引用；返回按账单国家重算后的当前报价。 */
@Getter
@AllArgsConstructor
public class SaveConsumerResponseBody {
    /** 当前 Checkout 的公开订单号。 */
    private final String orderNo;

    /** 保存完成后为 COMPLETE。 */
    private final String consumerStatus;

    /**
     * 重算后的金额状态（OrderAmountStatus Code）。
     * <li>FINAL：按账单国家计算完成或税务不由平台计算，可继续确认条款并付款。</li>
     * <li>BLOCKED：无法正确计税，税额与总额为 null，原因见 taxBlockReason，暂时不能付款；可更换账单地址后重新保存。</li>
     * <li>税务服务上线前创建的历史订单不重算，沿用原状态。</li>
     */
    private final String amountStatus;

    /**
     * 税务阻断原因代码。
     * <li>仅 amountStatus=BLOCKED 时有值，如 MARKET_NOT_ENABLED、NO_RULE；其他状态为 null。</li>
     */
    private final String taxBlockReason;

    /** 订单币种 Code。 */
    private final String currency;

    /** 重算后的订单金额；BLOCKED 时税额、舍入额与总额为 null。 */
    private final CheckoutAmountsInfo amounts;
}
