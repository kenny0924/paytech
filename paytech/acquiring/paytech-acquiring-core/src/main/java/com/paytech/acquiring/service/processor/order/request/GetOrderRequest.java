package com.paytech.acquiring.service.processor.order.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 订单查询输入；两个订单标识必须且只能提供一个，不增加 GET Body。 */
@Getter
@AllArgsConstructor
public class GetOrderRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** Commerce 公开订单号；按 Client 订单号查询时为空。 */
    private final String orderNo;

    /** Client 内永久唯一订单号；按 Commerce 订单号查询时为空。 */
    private final String clientOrderId;
}
