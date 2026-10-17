package com.paytech.commerce.integration.payment;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;

/** 未装配真实 Payment 前失败关闭，保留本地预占和可恢复任务。 */
@Component
public class UnavailablePaymentRefundClient implements PaymentRefundClient {
    /** 接入正式 Payment 契约前报告依赖不可用，调用方保留可恢复状态。 */
    @Override
    public Result submitOrQuery(Request request) {
        // TODO(Payment)：接入 Owner API JAR 和 Foundation 服务发现客户端，禁止默认退款成功。
        throw new ErrorException("Payment 退款接口尚未接入");
    }
}
