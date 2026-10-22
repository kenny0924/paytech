package com.paytech.acquiring.service.processor.order.response;

/** 单个订单过期收尾的结果，供扫描统计；除 EXPIRED 外订单均保持原状态。 */
public enum ExpireOrderResponse {
    /** 订单与实际关联的会话已置为 EXPIRED，order.expired 已登记。 */
    EXPIRED,

    /** Payment 仍有未决动作，终止意图已写入，等待下一轮。 */
    PENDING,

    /** Payment 已收款，等待 PaymentSucceeded 将订单置为 PAID。 */
    ALREADY_PAID,

    /** Payment 有未释放授权，已告警转人工，不自动撤销。 */
    REVERSAL_REQUIRED,

    /** 订单已终结、已支付或尚未到期，未做任何写入。 */
    SKIPPED,

    /** Payment 暂不可用，终止意图之外未做写入，下一轮重试。 */
    RETRY
}
