package com.paytech.commerce.test.checker.customer;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.commerce.customer.api.v1.checkout.response.GetCheckoutResponseBody;
import com.paytech.commerce.customer.api.v1.payment.response.PaymentSubmissionResponseBody;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** 公共协议和明确 DTO 检查，不使用 JSON 字段路径。 */
public class CheckoutFlowResponseChecker
        extends AbstractHttpResponseChecker<CheckoutFlowResponseChecker> {
    private final ObjectMapper mapper;

    public CheckoutFlowResponseChecker(
            EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    @Override
    protected CheckoutFlowResponseChecker self() {
        return this;
    }

    public CheckoutFlowResponseChecker assertReady(boolean consumerPresent) {
        assertStatus(200).assertRequestId();
        var body = mapper.readValue(result.getResponseBody(), GetCheckoutResponseBody.class);
        assertEquals("READY", body.getPreparationStatus());
        assertEquals("FINAL", body.getAmountStatus());
        assertEquals("1.00", body.getAmounts().getTotalAmount());
        assertNotNull(body.getLegal());
        assertEquals(3, body.getLegal().getDocuments().size());
        assertNull(body.getPayment());
        assertNull(body.getPollAfterMs());
        assertEquals(
                consumerPresent ? List.of("SAVE_CONSUMER", "PAY") : List.of("SAVE_CONSUMER"),
                body.getAllowedActions());
        return this;
    }

    public CheckoutFlowResponseChecker assertProcessing() {
        assertStatus(202).assertRequestId();
        var body = mapper.readValue(result.getResponseBody(), PaymentSubmissionResponseBody.class);
        assertEquals("PROCESSING", body.getStatus());
        assertNotNull(body.getSubmissionId());
        return this;
    }

    /** 读取支付提交响应体，供调用方逐项断言。 */
    public PaymentSubmissionResponseBody paymentBody() {
        return mapper.readValue(result.getResponseBody(), PaymentSubmissionResponseBody.class);
    }
}
