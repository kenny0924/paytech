package com.paytech.acquiring.service.processor.order.response;

/** 支付成功事件的落地结果；监听器据此记录日志，均表示可以确认消息。 */
public enum ApplyPaymentSucceededResponse {
    /** 订单、会话、支付投影、退款基数与资金投影已一次推进。 */
    APPLIED,

    /** 同一成功事件或同一成功尝试已经落地，未重复执行。 */
    DUPLICATE,

    /** 订单已取消或过期：只记录支付投影，订单与会话保持原状，待人工处理。 */
    RECORDED_FOR_CLOSED_ORDER
}
