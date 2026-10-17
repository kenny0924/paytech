package com.paytech.acquiring.service.processor.refund.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可退款金额查询的内部输入，字段约束由 HTTP 入口校验。 */
@Getter
@AllArgsConstructor
public class GetRefundableAmountRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 当前 Client 所属的公开订单号。 */
    private final String orderNo;
}
