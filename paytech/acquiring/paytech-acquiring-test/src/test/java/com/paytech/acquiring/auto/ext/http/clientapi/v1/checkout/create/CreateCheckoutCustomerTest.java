package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.enums.RiskDecisionResult;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.dal.CheckoutCreationTestMapper;
import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;
import com.paytech.acquiring.test.mocker.external.CounterpartyMocker;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * 建单关联 Customer（需求 20261015 commerce-Customer与存卡支付，AC-02、AC-03）：经真实创建入口与 MySQL；Customer 的建或取经
 * counterparty 内部合同（{@link CounterpartyMocker} 返回 counterparty-api DTO），订单只保存 Customer 主键。
 *
 * @see ClientCheckoutApi
 */
class CreateCheckoutCustomerTest extends AbstractCreateCheckoutApiTest {
    private static final long CUSTOMER_ID = 880_101L;
    private static final String CUSTOMER_NO = "cus_createcheckoutcustomertest00001";

    @Autowired
    private CheckoutCreationTestMapper orderMapper;

    /** 第二层 BeforeEach：仅准备本类各场景共用的商业事实 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 带引用：建或取 Customer 并关联；第二单复用同一 Customer；不带引用的访客订单不调用 counterparty。 */
    @Test
    void test_attachCustomer() {
        counterpartyMocker.mockCustomer(clientId(), "user-1", CUSTOMER_ID, CUSTOMER_NO);

        invoke(template().clientOrderId("order-1").consumerReference("user-1").build()).assertCreated("order-1",
            NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        invoke(template().clientOrderId("order-2").consumerReference("user-1").build());
        invoke(template().clientOrderId("order-3").build());

        var resolve = new CounterpartyMocker.Call("RESOLVE", Long.toString(clientId()), null, "user-1");
        assertAll(() -> assertEquals(List.of(resolve, resolve), counterpartyMocker.calls()),
            () -> assertEquals(CUSTOMER_ID, orderMapper.orderCustomerId(clientId(), "order-1")),
            () -> assertEquals(CUSTOMER_ID, orderMapper.orderCustomerId(clientId(), "order-2")),
            () -> assertNull(orderMapper.orderCustomerId(clientId(), "order-3")));
    }

    /** Risk 拒绝时在关联 Customer 之前失败：422，不建订单也不调用 counterparty。 */
    @Test
    void test_riskDeniedWithoutCustomer() {
        riskMocker.replies(CheckoutRiskMocker.result(RiskDecisionResult.DENY, 7201L));

        var response = invoke(template().consumerReference("user-1").build());

        response.assertProblem(422, CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE.getCode());
        assertAll(() -> assertTrue(counterpartyMocker.calls().isEmpty()),
            () -> checkoutChecker.assertOrderCount(clientId(), otherClientId(), 0));
        riskMocker.assertCalls(1);
    }

    /** 像电话的引用 422，不建订单也不调用 counterparty。 */
    @Test
    void test_personalReferenceRejected() {
        var response = invoke(template().consumerReference("13800138000").build());

        response.assertProblem(422, CounterpartyErrorCode.INVALID_CONSUMER_REFERENCE.getCode());
        assertAll(() -> assertTrue(counterpartyMocker.calls().isEmpty()),
            () -> checkoutChecker.assertOrderCount(clientId(), otherClientId(), 0));
    }

    /** counterparty 不可用时在订单事务前失败：503，不建订单；外部初始化已完成但不留下订单。 */
    @Test
    void test_consumerUnavailableCreatesNoOrder() {
        counterpartyMocker.mockResolveFailure(CounterpartyMocker.unavailable());

        var response = invoke(template().consumerReference("user-1").build());

        response.assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertAll(() -> assertEquals(1, counterpartyMocker.calls("RESOLVE").size()),
            () -> checkoutChecker.assertOrderCount(clientId(), otherClientId(), 0));
    }
}
