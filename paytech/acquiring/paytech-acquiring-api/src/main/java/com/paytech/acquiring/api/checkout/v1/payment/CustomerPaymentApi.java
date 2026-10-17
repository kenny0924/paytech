package com.paytech.commerce.customer.api.v1.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.commerce.customer.api.v1.payment.response.PaymentSubmissionResponseBody;

import jakarta.validation.Valid;

/** 所有 Customer 支付接口均由 Commerce 提供。 */
@HttpExchange(url = "/customer-gateway-api/v1/checkouts/{checkoutToken}", accept = "application/json")
public interface CustomerPaymentApi {
    /**
     * 提交当前卡信息与已展示合同的确认。
     *
     * @param token
     *            原 Checkout 公开 Token
     * @param body
     *            卡信息及明确的合同确认，卡原值不进入本地存储
     * @return 原提交当前结果；处理中或未知返回 202 和 Retry-After，其余返回 200
     */
    @PostExchange(value = "/payments", contentType = "application/json")
    ResponseEntity<PaymentSubmissionResponseBody> submitPayment(@PathVariable("checkoutToken") String token,
        @Valid @RequestBody SubmitPaymentRequestBody body);

    /**
     * 查询当前 Checkout 所属订单的一次支付提交，不创建或推进支付。
     *
     * @param token
     *            原 Checkout 公开 Token，按原订单期限校验
     * @param submissionId
     *            支付提交业务编号，必须为 Long 范围内的正十进制字符串
     * @return Payment 当前结果及已有消费者动作；未找到返回 404，访问过期返回 410
     */
    @GetExchange("/payment-submissions/{submissionId}")
    PaymentSubmissionResponseBody getPayment(@PathVariable("checkoutToken") String token,
        @PathVariable("submissionId") String submissionId);
}
