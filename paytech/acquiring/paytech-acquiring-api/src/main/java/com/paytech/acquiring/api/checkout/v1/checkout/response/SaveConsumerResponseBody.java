package com.paytech.commerce.customer.api.v1.checkout.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 保存结果不回显个人原值或保护引用。 */
@Getter
@AllArgsConstructor
public class SaveConsumerResponseBody {
    /** 当前 Checkout 的公开订单号。 */
    private final String orderNo;

    /** 保存完成后为 COMPLETE。 */
    private final String consumerStatus;
}
