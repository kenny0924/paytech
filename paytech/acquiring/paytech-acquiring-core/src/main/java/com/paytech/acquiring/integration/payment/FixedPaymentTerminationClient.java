package com.paytech.acquiring.integration.payment;

import org.springframework.stereotype.Component;

/** 本轮授权的 Payment 终止固定 Mock；待接正式终止接口。 */
@Component
public class FixedPaymentTerminationClient implements PaymentTerminationClient {
    /** 临时返回确定终止，用于验证 Commerce 取消编排。 */
    @Override
    public Result terminate(Request request) {
        // TODO(PAY-I12): 接入按 Client + COMMERCE_ORDER + orderId 定位的取消能力，映射业务 NOT_FOUND，禁止调用创建接口。
        return new Result(request.orderId(), Status.TERMINATED);
    }
}
