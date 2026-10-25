package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static com.paytech.acquiring.test.mocker.external.CheckoutCommercialMocker.problem;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.commercial.api.enums.CheckoutErrorCode;

/**
 * 创建时完成准备，依赖失败不保存半成品；commercial 解析经真实客户端访问测试 HTTP Mock。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutInitializationTest extends AbstractCreateCheckoutApiTest {
    /** 第二层 BeforeEach：本类共用 commercial 默认解析与 Risk 放行。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 没有合格经营主体：422 CLIENT_CONFIGURATION_NOT_READY，不落订单。 */
    @Test
    void test_noEligibleEntity() {
        preparationMocker.replies(clientId(), problem(HttpStatus.UNPROCESSABLE_CONTENT, CheckoutErrorCode.NO_ELIGIBLE_ENTITY));
        invoke(template().build()).assertProblem(422, CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        preparationMocker.assertCalls(clientId(), 1);
    }

    /** OE 编号缺失属于协议错误：502，不落订单。 */
    @Test
    void test_invalidCommercialFacts() {
        preparationMocker.mockResolution(clientId(), "US", "USD",
            CheckoutContextResponseBuilder.template(clientId()).with(body -> body.setEntityNo(null)).build());
        invoke(template().build()).assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }

    /** 退款政策文件证据缺失：502，不落订单。 */
    @Test
    void test_missingLegalDocument() {
        preparationMocker.mockResolution(clientId(), "US", "USD", CheckoutContextResponseBuilder.template(clientId())
            .with(body -> body.getLegal().getDocuments().removeIf(file -> "202".equals(file.getDocumentId()))).build());
        invoke(template().build()).assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }

    /** commercial 暂不可用时返回 503 且不落订单；恢复后同一业务订单号可以重新创建。 */
    @Test
    void test_retryAfterDependencyUnavailable() {
        preparationMocker.replies(clientId(), problem(HttpStatus.SERVICE_UNAVAILABLE, CheckoutErrorCode.OWNER_UNAVAILABLE));
        invoke(template().build()).assertProblem(503, CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        var response =
            invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, response.orderNo(), NOW, NOW.plusSeconds(1800));
        preparationMocker.assertCalls(clientId(), 2);
    }

    /** 准备期间订单期限耗尽：422 CHECKOUT_NOT_ELIGIBLE，不落订单。 */
    @Test
    void test_expiryDuringPreparation() {
        preparationMocker.observe(request -> adjustableUtcClock.advance(Duration.ofMinutes(30)));
        invoke(template().build()).assertProblem(422, CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE.getCode());
        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
    }
}
