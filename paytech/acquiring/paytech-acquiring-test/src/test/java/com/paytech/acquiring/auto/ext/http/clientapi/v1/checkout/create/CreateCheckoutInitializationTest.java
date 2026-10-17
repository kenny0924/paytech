package com.paytech.commerce.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.commerce.test.builder.checkout.CreateCheckoutRequestBuilder.template;

import com.paytech.commerce.client.api.v1.checkout.ClientCheckoutApi;
import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.commerce.test.builder.external.CommercialFactsBuilder;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * 创建时完成准备，依赖失败不保存半成品；外部固定 Client 的 DTO 替身不覆盖 HTTP 解码。
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutInitializationTest extends AbstractCreateCheckoutApiTest {
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    @Test
    void test_noEligibleEntity() {
        preparationMocker.mockFailure(
                clientId(),
                new FailException(CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY));
        invoke(template().build()).assertProblem(422, "CLIENT_CONFIGURATION_NOT_READY");
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        preparationMocker.assertCalls(clientId(), 1);
    }

    @Test
    void test_invalidCommercialFacts() {
        preparationMocker.mockFacts(
                clientId(), "US", "USD", CommercialFactsBuilder.template().entityNo(null).build());
        invoke(template().build()).assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }

    @Test
    void test_missingLegalDocument() {
        preparationMocker.mockFacts(
                clientId(),
                "US",
                "USD",
                CommercialFactsBuilder.template().refundPolicy(null).build());
        invoke(template().build()).assertProblem(502, "UPSTREAM_PROTOCOL_ERROR");
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }

    @Test
    void test_retryAfterDependencyUnavailable() {
        preparationMocker.mockFailure(
                clientId(), new ServiceUnavailableException(new IllegalStateException("CCT 暂不可用")));
        invoke(template().build()).assertProblem(503, "SERVICE_UNAVAILABLE");
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        mockDefaultPreparation();
        var response =
                invoke(template().build())
                        .assertCreated(
                                CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        checkoutChecker.assertCreated(
                clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, NOW.plusSeconds(1800));
        preparationMocker.assertCalls(clientId(), 2);
    }

    @Test
    void test_expiryDuringPreparation() {
        preparationMocker.observe(request -> adjustableUtcClock.advance(Duration.ofMinutes(30)));
        invoke(template().build()).assertProblem(422, "CHECKOUT_NOT_ELIGIBLE");
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }
}
