package com.paytech.commerce.ext.rest.external.customerapi.v1.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.commerce.customer.api.v1.payment.CustomerPaymentApi;
import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.commerce.customer.api.v1.payment.response.PaymentSubmissionResponseBody;
import com.paytech.commerce.service.processor.customer.GetCustomerPaymentProcessor;
import com.paytech.commerce.service.processor.customer.SubmitCustomerPaymentProcessor;

import lombok.AllArgsConstructor;

/** Customer 支付 HTTP 入口，仅将协议参数交给对应 Processor。 */
@RestController
@AllArgsConstructor
public class CustomerPaymentController implements CustomerPaymentApi {
    private final GetCustomerPaymentProcessor getCustomerPaymentProcessor;
    private final SubmitCustomerPaymentProcessor submitCustomerPaymentProcessor;

    /** 只转换当前连接与请求字段、委托 Processor 并映射 HTTP 状态。 */
    @Override
    public ResponseEntity<PaymentSubmissionResponseBody> submitPayment(String token,
        SubmitPaymentRequestBody body) {
        var http = ((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest();
        var request = CustomerPaymentConverter.request(token, body, http);
        var result = submitCustomerPaymentProcessor.process(request);
        var response = CustomerPaymentConverter.response(result);
        return result.isPending() ? ResponseEntity.accepted().header("Retry-After", "2").body(response)
            : ResponseEntity.ok(response);
    }

    /** 转换查询路径、委托 Processor，再将内部结果转换为响应体。 */
    @Override
    public PaymentSubmissionResponseBody getPayment(String token, String submissionId) {
        var request = CustomerPaymentConverter.request(token, submissionId);
        var result = getCustomerPaymentProcessor.process(request);
        return CustomerPaymentConverter.response(result);
    }
}
