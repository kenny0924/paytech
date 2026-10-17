package com.paytech.commerce.service.processor.order.response;

import java.time.Instant;

import com.paytech.commerce.domain.enums.CheckoutSessionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从订单事实构造的取消结果。 */
@Getter
@AllArgsConstructor
public class CancelOrderResponse {
    /** 公开订单号。 */
    String orderNo;

    /** 是否仍需等待 Payment 的确定终止结果。 */
    boolean pending;

    /** 原始取消确认时间；待定时为空。 */
    Instant cancelledAt;

    /** 实际会话的取消终态；待定或未关联会话时为空。 */
    CheckoutSessionStatus checkoutStatus;
}