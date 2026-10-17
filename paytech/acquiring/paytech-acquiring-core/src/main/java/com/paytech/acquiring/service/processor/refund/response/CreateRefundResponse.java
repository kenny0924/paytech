package com.paytech.commerce.service.processor.refund.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从原退款或本次保存事实构造的受理结果。 */
@Getter
@AllArgsConstructor
public class CreateRefundResponse {
    /** 公开退款号。 */
    private final String refundRequestNo;

    /** Client 业务退款号。 */
    private final String clientRefundId;

    /** 公开订单号。 */
    private final String orderNo;

    /** 已按币种精度输出的主单位金额。 */
    private final String amount;

    /** 订单币种。 */
    private final String currency;

    /** 原退款创建时刻。 */
    private final Instant createdAt;
}
