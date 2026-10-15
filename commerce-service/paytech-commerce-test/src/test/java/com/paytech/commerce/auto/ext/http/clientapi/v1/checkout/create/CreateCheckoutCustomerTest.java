package com.paytech.commerce.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.commerce.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commerce.client.api.v1.checkout.ClientCheckoutApi;
import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.commerce.test.dal.CustomerTestMapper;

/**
 * 建单关联 Customer（需求 20261015 commerce-Customer与存卡支付，AC-02、AC-03）：经真实创建入口与 MySQL。
 *
 * @see ClientCheckoutApi
 */
class CreateCheckoutCustomerTest extends AbstractCreateCheckoutApiTest {
    @Autowired
    private CustomerTestMapper customerMapper;

    /** 第二层 BeforeEach：仅准备本类各场景共用的商业事实 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 带引用：新建 Customer 并关联；第二单复用同一 Customer；不带引用的访客订单不建 Customer。 */
    @Test
    void test_attachCustomer() {
        invoke(template().clientOrderId("order-1").consumerReference("user-1").build()).assertCreated("order-1",
            NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        invoke(template().clientOrderId("order-2").consumerReference("user-1").build());
        invoke(template().clientOrderId("order-3").build());

        var customers = customerMapper.customers(clientId(), otherClientId());
        Long first = customerMapper.orderCustomerId(clientId(), "order-1");
        assertAll(() -> assertEquals(1, customers.size()),
            () -> assertEquals(((Number)customers.getFirst().get("customerId")).longValue(), first),
            () -> assertEquals(first, customerMapper.orderCustomerId(clientId(), "order-2")),
            () -> assertNull(customerMapper.orderCustomerId(clientId(), "order-3")));
    }

    /** 像电话的引用 422，不建订单也不建 Customer。 */
    @Test
    void test_personalReferenceRejected() {
        var response = invoke(template().consumerReference("13800138000").build());

        response.assertProblem(422, CommerceCheckoutErrorCode.INVALID_CONSUMER_REFERENCE.getCode());
        assertAll(() -> assertTrue(customerMapper.customers(clientId(), otherClientId()).isEmpty()),
            () -> checkoutChecker.assertOrderCount(clientId(), otherClientId(), 0));
    }
}
