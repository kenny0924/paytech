package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.item;
import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.acquiring.test.checker.checkout.CreateCheckoutResponseChecker;

/**
 * 业务订单号防重、Client 隔离及并发竞争。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutDuplicateTest extends AbstractCreateCheckoutApiTest {
    /** 第二层 BeforeEach：仅准备本类各场景共用的 CCT 预检 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 已占用业务编号在任何订单状态下都不能重新下单，也不能再次预检。 */
    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "PAID", "CANCELLED", "EXPIRED"})
    void test_existingBusinessOrderIsRejected(String state) {
        long orderId = checkoutDataMocker.mockExistingOrder(CLIENT_ORDER_ID, state, NOW);
        invoke(template().build()).assertProblem(409, "CLIENT_ORDER_ID_ALREADY_EXISTS");
        checkoutChecker.assertExistingUnchanged(clientId(), CLIENT_ORDER_ID, orderId, OrderStatus.valueOf(state), NOW);
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 1);
        preparationMocker.assertNoCalls();
    }

    /** 一次成功后的同业务请求继续返回业务冲突，不重放第一次 HTTP 响应。 */
    @Test
    void test_duplicateSubmissionDoesNotCreateAnotherOrder() {
        var request = template().build();
        var original = invoke(request).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        invoke(request).assertProblem(409, "CLIENT_ORDER_ID_ALREADY_EXISTS");
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, original.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 1);
        preparationMocker.assertCalls(clientId(), 1);
    }

    /** 改内容也不能复用已占用的业务编号，旧订单金额和关联保持原样。 */
    @Test
    void test_sameBusinessIdentityWithChangedContentIsRejected() {
        long orderId = checkoutDataMocker.mockExistingOrder(CLIENT_ORDER_ID, "CREATED", NOW);
        var request = template().items(List.of(item("new-line", "Changed purchase", "1", "99", null))).build();

        invoke(request).assertProblem(409, "CLIENT_ORDER_ID_ALREADY_EXISTS");

        checkoutChecker.assertExistingUnchanged(clientId(), CLIENT_ORDER_ID, orderId, OrderStatus.CREATED, NOW);
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 1);
        preparationMocker.assertNoCalls();
    }

    /** 相同业务编号属于不同 Client 时互不占用。 */
    @Test
    void test_clientOrderIdHasClientScope() {
        preparationMocker.mockResolution(otherClientId(), "US", "USD",
            CheckoutContextResponseBuilder.template(otherClientId()).build());
        var first =
            invoke(template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
        var second = invoke(otherClientId(), template().build()).assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800),
            "44.90", "2.00", "42.90");
        assertNotEquals(first.orderNo(), second.orderNo());
        checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, first.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertCreated(otherClientId(), CLIENT_ORDER_ID, second.orderNo(), NOW, NOW.plusSeconds(1800));
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 2);
        preparationMocker.assertCalls(clientId(), 1);
        preparationMocker.assertCalls(otherClientId(), 1);
    }

    /** 两个请求同时通过存在性检查，唯一索引仍只允许一份完整创建事实。 */
    @Test
    void test_concurrentDuplicateSubmission() throws Exception {
        var barrier = new CyclicBarrier(2);
        preparationMocker.observe(call -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError("两个请求应同时到达 CCT，随后竞争业务唯一键", failure);
            }
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> invoke(template().build()));
            var second = executor.submit(() -> invoke(template().build()));
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409),
                results.stream().map(CreateCheckoutResponseChecker::status).sorted().toList());
            var winner = results.stream().filter(result -> result.status() == 201).findFirst().orElseThrow();
            var loser = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
            winner.assertCreated(CLIENT_ORDER_ID, NOW.plusSeconds(1800), "44.90", "2.00", "42.90");
            loser.assertProblem(409, "CLIENT_ORDER_ID_ALREADY_EXISTS");
            checkoutChecker.assertCreated(clientId(), CLIENT_ORDER_ID, winner.orderNo(), NOW, NOW.plusSeconds(1800));
        }
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 1);
        preparationMocker.assertCalls(clientId(), 2);
        assertEquals(List.of(false, false), cctTransactions.transactionStates());
    }
}
