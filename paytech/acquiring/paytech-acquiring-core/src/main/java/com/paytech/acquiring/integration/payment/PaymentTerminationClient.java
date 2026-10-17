package com.paytech.commerce.integration.payment;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Commerce 本地终止端口；真实 Payment HTTP 合同仍待接入。 */
public interface PaymentTerminationClient {
    /**
     * 只取消 Client 下 COMMERCE_ORDER + orderId 对应的已有 Intent，绝不创建。
     * Intent 不存在抛出 NOT_FOUND 的 FailException；适配器不得将路由 404 或超时映射为该业务错误。
     */
    Result terminate(Request request);

    /** 以订单业务身份定位，创建响应丢失时也无需提前获取 Intent ID。 */
    record Request(long clientId, long orderId) {
    }

    /** 只返回原订单的终止决定，不在取消链路补写支付投影。 */
    record Result(long orderId, Status status) {
    }

    @Getter
    @RequiredArgsConstructor
    enum Status implements CodeEnum {
        TERMINATED("TERMINATED"),

        PENDING("PENDING"),

        REVERSAL_REQUIRED("REVERSAL_REQUIRED");

        private final String code;
    }
}
