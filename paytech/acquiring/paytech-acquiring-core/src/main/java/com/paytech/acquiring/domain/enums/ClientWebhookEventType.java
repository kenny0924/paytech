package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Commerce 本期产生的 Client Webhook 事件类型；Code 为合同公开类型，短名只用于拼接事件 ID。 */
@Getter
@AllArgsConstructor
public enum ClientWebhookEventType implements CodeEnum {
    /** 订单首次支付成功。 */
    ORDER_PAID("order.paid", "paid"),

    /** 订单首次取消完成。 */
    ORDER_CANCELLED("order.cancelled", "cancelled"),

    /** 订单首次过期收尾完成。 */
    ORDER_EXPIRED("order.expired", "expired"),

    /** 退款请求首次受理。 */
    REFUND_CREATED("refund.created", "created");

    /** 对外稳定事件类型。 */
    private final String code;

    /** 事件 ID 后缀；每种事件对同一资源最多发生一次。 */
    private final String shortName;

    /** 资源公开编号对应的永久事件 ID：evt_ + 编号 + _ + 短名，重放不变。 */
    public String eventId(String resourceNo) {
        return "evt_" + resourceNo + "_" + shortName;
    }
}
