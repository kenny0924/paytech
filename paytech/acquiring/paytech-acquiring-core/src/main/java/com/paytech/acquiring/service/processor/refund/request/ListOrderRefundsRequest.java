package com.paytech.acquiring.service.processor.refund.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询订单退款列表的内部输入，HTTP 参数约束由入口处理。 */
@Getter
@AllArgsConstructor
public class ListOrderRefundsRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 当前 Client 的公开订单号。 */
    private final String orderNo;

    /** 本页最多返回的退款数量。 */
    private final int limit;

    /** 上一页返回的游标；首屏为空。 */
    private final String cursor;
}
