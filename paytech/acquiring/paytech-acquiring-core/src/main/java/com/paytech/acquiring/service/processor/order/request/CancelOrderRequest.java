package com.paytech.acquiring.service.processor.order.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 取消订单的内部输入；HTTP 字段约束由入口统一校验。 */
@Getter
@AllArgsConstructor
public class CancelOrderRequest {
    /** 可信接入层确定的 Client 主键。 */
    private final long clientId;

    /** 当前 Client 的公开订单号。 */
    private final String orderNo;

}
