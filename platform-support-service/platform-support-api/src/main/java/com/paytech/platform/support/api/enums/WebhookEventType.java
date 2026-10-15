package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client Webhook V1 公开事件类型；HTTP 字段仍使用 String，未知代码由消费方明确拒绝。 */
public enum WebhookEventType implements CodeEnum {

    /** 订单支付成功。 */
    ORDER_PAID("order.paid", true),

    /** 订单取消。 */
    ORDER_CANCELLED("order.cancelled", true),

    /** 订单过期。 */
    ORDER_EXPIRED("order.expired", true),

    /** 订单资金可用。 */
    ORDER_FUNDS_AVAILABLE("order.funds_available", true),

    /** 退款已创建。 */
    REFUND_CREATED("refund.created", true),

    /** 退款成功。 */
    REFUND_SUCCEEDED("refund.succeeded", true),

    /** 退款失败。 */
    REFUND_FAILED("refund.failed", true),

    /** 退款取消。 */
    REFUND_CANCELLED("refund.cancelled", true),

    /** 运营测试事件；不能被订阅或由来源系统提交。 */
    WEBHOOK_PING("webhook.ping", false);

    private final String code;

    private final boolean subscribable;

    /** 固定发布代码，不依赖 Java 常量名。 */
    WebhookEventType(String code, boolean subscribable) {
        this.code = code;
        this.subscribable = subscribable;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 是否允许端点订阅和来源系统提交。 */
    public boolean isSubscribable() {
        return subscribable;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static WebhookEventType fromCode(String code) {
        for (var value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
