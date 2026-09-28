package com.paytech.commerce.ext.rest.external.customerapi.v1.payment;

import static com.paytech.commerce.customer.api.v1.enums.CustomerPaymentStatus.*;

import org.slf4j.MDC;

import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.commerce.customer.api.v1.payment.response.PaymentSubmissionResponseBody;
import com.paytech.commerce.customer.api.v1.payment.response.PaymentSubmissionResponseBody.*;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CardIdentity;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.CardInput;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.NetworkEvidence;
import com.paytech.commerce.service.processor.customer.request.GetCustomerPaymentRequest;
import com.paytech.commerce.service.processor.customer.request.SubmitCustomerPaymentRequest;
import com.paytech.commerce.service.processor.customer.response.CustomerPaymentResponse;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.request.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/** 支付入站模型与内部模型转换，只处理协议字段和受信连接信息。 */
final class CustomerPaymentConverter {
    /** 仅提供无状态转换。 */
    private CustomerPaymentConverter() {}

    /** 查询路径转换为内部输入，不增加 GET Body 或解释资源归属。 */
    static GetCustomerPaymentRequest request(String checkoutToken, String submissionId) {
        return new GetCustomerPaymentRequest(checkoutToken, id(submissionId));
    }

    /** 网络原值来自当前连接；TODO(Gateway)：公网接入前对齐受信消费者 IP 合同。 */
    static SubmitCustomerPaymentRequest request(String token, SubmitPaymentRequestBody body,
        HttpServletRequest http) {
        var card = body.getCard();
        var legal = body.getLegalConfirmation();
        var identity = new CardIdentity(card.getPan(), card.getExpiryMonth(), card.getExpiryYear(),
            card.getCardholderName());
        String userAgent = http.getHeader("User-Agent");
        var evidence = new NetworkEvidence(http.getRemoteAddr(), userAgent == null ? "" : userAgent, null);
        return new SubmitCustomerPaymentRequest(token, id(legal.getConsumerContractId()), legal.getConfirmed(),
            new CardInput(identity, card.getCvv()), evidence, MDC.get(RequestIdFilter.MDC_KEY));
    }

    /** ID 必须为范围内的规范正十进制，错误消息不回显输入。 */
    static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
                throw new NumberFormatException();
            }
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException("标识必须为有效正十进制字符串");
        }
    }

    /** 只投影 Payment 受限事实，未知传输结果不伪造 observedAt 或支付成功。 */
    static PaymentSubmissionResponseBody response(CustomerPaymentResponse view) {
        var result = view.getPayment();
        var reference = view.getSubmission();
        if (result == null) {
            return new PaymentSubmissionResponseBody(Long.toString(reference.submissionId()), view.getExpiresAt(), null,
                PROCESSING.getCode(), null, null, null, null, 2000);
        }
        var action = result.nextAction();
        return new PaymentSubmissionResponseBody(Long.toString(reference.submissionId()), view.getExpiresAt(),
            result.attemptId(), result.status(), result.last4() == null ? null : new CardSummaryInfo(result.last4()),
            action == null ? null
                : new NextActionInfo(action.actionId(), action.type(), action.expiresAt(), action.redirectUrl()),
            result.failureCode() == null ? null : new FailureInfo(result.failureCode(), "本次支付未完成"), result.observedAt(),
            view.isPending() ? 2000 : null);
    }
}
