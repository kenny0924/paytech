package com.paytech.acquiring.service.processor.refund.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可退款金额的查询结果，不携带数据库对象或内部主键。 */
@Getter
@AllArgsConstructor
public class GetRefundableAmountResponse {
    /** 当前 Client 所属的公开订单号。 */
    private final String orderNo;

    /** 退款位置的币种，与订单币种一致。 */
    private final String currency;

    /** 按币种精度输出的主单位可退款金额。 */
    private final String refundableAmount;

    /** 本次读取的退款位置最后持久化的 UTC 时刻。 */
    private final Instant asOf;

    /** 本次读取的退款位置版本。 */
    private final long version;
}
