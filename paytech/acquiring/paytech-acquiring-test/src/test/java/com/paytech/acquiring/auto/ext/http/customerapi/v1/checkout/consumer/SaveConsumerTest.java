package com.paytech.commerce.auto.ext.http.customerapi.v1.checkout.consumer;

import static com.paytech.commerce.test.builder.customer.SaveConsumerRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.customer.api.v1.checkout.CustomerCheckoutApi;
import com.paytech.commerce.customer.api.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.commerce.domain.checkout.CreateCheckoutContext;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.checker.customer.SaveConsumerChecker;
import com.paytech.commerce.test.checker.customer.SaveConsumerResponseChecker;
import com.paytech.commerce.test.checker.order.OrderCancellationChecker;
import com.paytech.commerce.test.mocker.external.CheckoutCardMocker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.commerce.test.mocker.external.ConsumerProtectionMocker;
import com.paytech.commerce.test.mocker.external.PaymentTerminationMocker;
import com.paytech.commerce.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.commerce.test.mocker.local.OrderCancellationDataMocker;
import com.paytech.commerce.test.mocker.local.SaveConsumerDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.foundation.token.PublicTokenGenerator;

import tools.jackson.databind.JsonNode;

/**
 * 消费者完整保存：规范化、访问资格、外部保护失败及真实并发，不创建 Intent 或法律接受。
 *
 * @see CustomerCheckoutApi#saveConsumer(String, SaveConsumerRequestBody)
 */
class SaveConsumerTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.SaveConsumer.Test";
    @Autowired
    private CheckoutPaymentDataMocker orders;
    @Autowired
    private SaveConsumerDataMocker data;
    @Autowired
    private OrderCancellationDataMocker acceptances;
    @Autowired
    private ConsumerProtectionMocker protection;
    @Autowired
    private SaveConsumerChecker checker;
    @Autowired
    private OrderCancellationChecker orderChecker;
    @Autowired
    private CheckoutIntentMocker intents;
    @Autowired
    private CheckoutCardMocker cards;
    @Autowired
    private PaymentTerminationMocker terminations;
    private CreateCheckoutContext fixture;
    private ConsumerDetails details;
    private ProtectedConsumer protectedValues;

    /** 每例重新创建未接受法律条款的订单，以及精确匹配的外部 DTO。 */
    @BeforeEach
    void setUpConsumerMocks() {
        fixture = orders.mockPrepared(false);
        details = template().details();
        protectedValues = new ProtectedConsumer("test-email-reference", "test-profile-reference");
        protection.mockResult(details, protectedValues);
    }

    /** 创建后允许补充资料；国家变化不重新选择 OE、计税或创建支付。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_saveBeforeAcceptance(boolean legacyCreated) {
        if (legacyCreated) {
            data.changeOrder(orderId(), order -> order.setOrderStatus(OrderStatus.CREATED));
            data.changeSession(orderId(), CheckoutSessionStatus.PREPARING);
        }
        var other = orders.mockPrepared(true);
        var otherBefore = checker.snapshot(other.getOrder().getOrderId());
        var before = orderChecker.order(orderId());
        var savedAt =
            adjustableUtcClock.advance(Duration.ofNanos(123456789)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        invoke(template().build()).assertSaved(before.getOrderNo());

        checker.assertSaved(orderId(), details, protectedValues, 1, savedAt, savedAt);
        checker.assertOrderChangedOnlyVersion(before, savedAt);
        assertEquals(otherBefore, checker.snapshot(other.getOrder().getOrderId()));
        protection.assertCalls(details, 1);
    }

    /** 首尾空白和 NFC 在调用外部服务前规范化；保留姓名内部空格与邮箱 local-part。 */
    @Test
    void test_normalizesBeforeProtection() {
        var input = template().fullName("  Jose\u0301  Test  ").email(" Buyer+Tag@BÜCHER.Example ")
            .line1("  10 Cafe\u0301 Street  ").line2("  Unit 2  ").region(" DU ").city("  Cafe\u0301 City  ")
            .postalCode(" 00000 ");
        details = template().fullName("José  Test").email("Buyer+Tag@xn--bcher-kva.example").line1("10 Café Street")
            .city("Café City").details();
        protection.mockResult(details, protectedValues);

        invoke(input.build()).assertSaved(fixture.getOrder().getOrderNo());

        checker.assertSaved(orderId(), details, protectedValues, 1, adjustableUtcClock.instant(),
            adjustableUtcClock.instant());
        protection.assertCalls(details, 1);
    }

    /** 合法长度上界可写入真实表，保护引用也允许恰好 128 个字符。 */
    @Test
    void test_maximumLengths() {
        var input = template().fullName("N".repeat(200))
            .email("b".repeat(64) + "@" + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(61))
            .line1("L".repeat(255)).line2("S".repeat(255)).region("R".repeat(64)).city("C".repeat(128))
            .postalCode("P".repeat(32));
        details = input.details();
        protectedValues = new ProtectedConsumer("e".repeat(128), "p".repeat(128));
        protection.mockResult(details, protectedValues);

        invoke(input.build()).assertSaved(fixture.getOrder().getOrderNo());

        checker.assertSaved(orderId(), details, protectedValues, 1, adjustableUtcClock.instant(),
            adjustableUtcClock.instant());
        protection.assertCalls(details, 1);
    }

    /** PUT 全量替换保留原身份和创建时间；可选字段省略与显式 null 都清空旧值。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_replaceAndClearOptionalFields(boolean omitNulls) {
        invoke(template().build()).assertSaved(fixture.getOrder().getOrderNo());
        var original = checker.consumer(orderId());
        var before = orderChecker.order(orderId());
        var replacement = template().fullName("Another Consumer").email("NewBuyer@example.test").country("GB")
            .line1("20 Other Street").line2(null).region(null).city("London").postalCode(null);
        details = replacement.details();
        protectedValues = new ProtectedConsumer("second-email-reference", "second-profile-reference");
        protection.mockResult(details, protectedValues);
        var savedAt = adjustableUtcClock.advance(Duration.ofSeconds(5));
        Object body = replacement.build();
        if (omitNulls) {
            body = objectMapper.rebuild()
                .changeDefaultPropertyInclusion(inclusion -> inclusion
                    .withValueInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
                .build().writeValueAsString(body);
        }

        invoke(body).assertSaved(fixture.getOrder().getOrderNo());

        checker.assertSaved(orderId(), details, protectedValues, 2, original.getCreateTime(), savedAt);
        assertEquals(original.getConsumerContextId(), checker.consumer(orderId()).getConsumerContextId());
        checker.assertOrderChangedOnlyVersion(before, savedAt);
        protection.assertCalls(List.of(template().details(), details));
    }

    /** 相同 PUT 重新校验并写入原记录，不要求幂等键或重放第一次响应。 */
    @Test
    void test_repeatedRequestUpdatesSameConsumer() {
        invoke(template().build()).assertSaved(fixture.getOrder().getOrderNo());
        var original = checker.consumer(orderId());
        var before = orderChecker.order(orderId());
        var savedAt = adjustableUtcClock.advance(Duration.ofSeconds(1));

        invoke(template().build()).assertSaved(fixture.getOrder().getOrderNo());

        checker.assertSaved(orderId(), details, protectedValues, 2, original.getCreateTime(), savedAt);
        assertEquals(original.getConsumerContextId(), checker.consumer(orderId()).getConsumerContextId());
        checker.assertOrderChangedOnlyVersion(before, savedAt);
        protection.assertCalls(details, 2);
    }

    /** 非法 DTO 在外部保护与任何业务写入之前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void test_invalidBody(String name, Consumer<SaveConsumerRequestBody> change) {
        var body = template().build();
        change.accept(body);
        reject(body, 422, CommonErrorCode.VALIDATION_ERROR.getCode());
    }

    /** 覆盖缺值、边界、控制字符、邮箱与国家规范；通过实际 DTO setter 修改字段。 */
    static Stream<Arguments> invalidBodies() {
        return Stream.of(invalid("姓名缺失", b -> b.setFullName(null)), invalid("姓名空白", b -> b.setFullName("  ")),
            invalid("姓名超长", b -> b.setFullName("n".repeat(201))), invalid("姓名控制字符", b -> b.setFullName("Test\nName")),
            invalid("邮箱缺失", b -> b.setEmail(null)), invalid("邮箱超长", b -> b.setEmail("x".repeat(255))),
            invalid("邮箱无分隔符", b -> b.setEmail("bad")), invalid("邮箱无本地部分", b -> b.setEmail("@example.invalid")),
            invalid("邮箱无域名", b -> b.setEmail("buyer@")), invalid("邮箱多个分隔符", b -> b.setEmail("a@b@example.invalid")),
            invalid("邮箱内部空白", b -> b.setEmail("a b@example.invalid")),
            invalid("邮箱域名无点", b -> b.setEmail("buyer@localhost")),
            invalid("非法 IDN 域名", b -> b.setEmail("buyer@bad_domain.invalid")),
            invalid("邮箱本地部分超长", b -> b.setEmail("a".repeat(65) + "@example.invalid")),
            invalid("地址缺失", b -> b.setBillingAddress(null)),
            invalid("国家缺失", b -> b.getBillingAddress().setCountryCode(null)),
            invalid("国家小写", b -> b.getBillingAddress().setCountryCode("ae")),
            invalid("国家不存在", b -> b.getBillingAddress().setCountryCode("ZZ")),
            invalid("国家超长", b -> b.getBillingAddress().setCountryCode("ARE")),
            invalid("第一行缺失", b -> b.getBillingAddress().setAddressLine1(null)),
            invalid("第一行空白", b -> b.getBillingAddress().setAddressLine1("  ")),
            invalid("第一行超长", b -> b.getBillingAddress().setAddressLine1("l".repeat(256))),
            invalid("第二行空串", b -> b.getBillingAddress().setAddressLine2("")),
            invalid("第二行空白", b -> b.getBillingAddress().setAddressLine2("  ")),
            invalid("第二行超长", b -> b.getBillingAddress().setAddressLine2("l".repeat(256))),
            invalid("地区空白", b -> b.getBillingAddress().setRegion("  ")),
            invalid("地区超长", b -> b.getBillingAddress().setRegion("r".repeat(65))),
            invalid("城市缺失", b -> b.getBillingAddress().setCity(null)),
            invalid("城市超长", b -> b.getBillingAddress().setCity("c".repeat(129))),
            invalid("城市控制字符", b -> b.getBillingAddress().setCity("City\tName")),
            invalid("邮编空白", b -> b.getBillingAddress().setPostalCode("  ")),
            invalid("邮编超长", b -> b.getBillingAddress().setPostalCode("p".repeat(33))));
    }

    /** 参数说明与 DTO 修改动作放在同一行，避免字符串字段路径。 */
    private static Arguments invalid(String name, Consumer<SaveConsumerRequestBody> change) {
        return Arguments.of(name, change);
    }

    /** 只有线协议错误使用原始 JSON；业务响应继续使用类型化 Checker。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"null", "{", "[]", "{\"fullName\":123}", "{\"fullName\":true}"})
    void test_malformedJson(String body) {
        reject(body, 400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 伪造 Token 和格式正确但不存在的 Token 都隐藏资源细节。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_unknownToken(boolean syntacticallyValid) {
        var before = checker.snapshot(orderId());
        String token =
            syntacticallyValid ? "chk_" + PublicTokenGenerator.generate("absent-consumer-checkout") : "bad-token";
        invoke(token, template().build()).assertProblem(404, CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND.getCode());
        assertEquals(before, checker.snapshot(orderId()));
        protection.assertNoCalls();
    }

    /** 会话关联订单缺失同样返回不存在。 */
    @Test
    void test_orderMissing() {
        data.removeOrder(orderId());
        reject(template().build(), 404, CustomerCheckoutErrorCode.CHECKOUT_NOT_FOUND.getCode());
    }

    /** 截止时刻即过期；状态为 EXPIRED 时也不能因未来时间继续编辑。 */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void test_expired(int seconds) {
        data.changeOrder(orderId(), order -> {
            order.setExpiresAt(adjustableUtcClock.instant().plusSeconds(seconds));
            if (seconds > 0) {
                order.setOrderStatus(OrderStatus.EXPIRED);
            }
        });
        reject(template().build(), 410, CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode());
    }

    /** 在途、完成和关闭的会话不能再改消费者资料。 */
    @ParameterizedTest
    @EnumSource(value = CheckoutSessionStatus.class, names = {"PREPARING", "OPEN"}, mode = EnumSource.Mode.EXCLUDE)
    void test_closedSession(CheckoutSessionStatus status) {
        data.changeSession(orderId(), status);
        reject(template().build(), 409, CustomerCheckoutErrorCode.CHECKOUT_CLOSED.getCode());
    }

    /** 订单业务事实优先于可编辑会话；旧准备标记仍保持锁定语义。 */
    @ParameterizedTest
    @ValueSource(strings = {"paid", "cancelled", "paidAt", "termination", "preparation", "accepted"})
    void test_orderCannotBeEdited(String scenario) {
        data.changeOrder(orderId(), order -> {
            switch (scenario) {
                case "paid" -> order.setOrderStatus(OrderStatus.PAID);
                case "cancelled" -> order.setOrderStatus(OrderStatus.CANCELLED);
                case "paidAt" -> order.setPaidAt(adjustableUtcClock.instant());
                case "termination" -> order.setTerminationRequestedAt(adjustableUtcClock.instant());
                case "preparation" -> order.setPreparationCommandId(orderId());
                default -> {
                }
            }
        });
        if (scenario.equals("accepted")) {
            acceptances.mockAcceptances(orderId(), adjustableUtcClock.instant());
        }
        var error = scenario.equals("preparation") || scenario.equals("accepted")
            ? CustomerCheckoutErrorCode.CONSUMER_DETAILS_LOCKED : CustomerCheckoutErrorCode.CHECKOUT_CLOSED;
        reject(template().build(), 409, error.getCode());
    }

    /** 超时不能产生本地资料或版本变化；恢复后同请求可以正常保存。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_protectionUnavailableThenRetry(boolean existing) {
        if (existing) {
            fixture = orders.mockPrepared(true);
        }
        var before = checker.snapshot(orderId());
        protection.mockFailure(details,
            new ServiceUnavailableException(new java.net.SocketTimeoutException("protect timeout")));
        invoke(template().build()).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertEquals(before, checker.snapshot(orderId()));
        protection.mockResult(details, protectedValues);
        invoke(template().build()).assertSaved(fixture.getOrder().getOrderNo());
        checker.assertSaved(orderId(), details, protectedValues, existing ? 2 : 1, adjustableUtcClock.instant(),
            adjustableUtcClock.instant());
        protection.assertCalls(details, 2);
    }

    /** 外部空响应及缺失、空白、超长 Token 均不落库。 */
    @ParameterizedTest
    @MethodSource("invalidProtectionResults")
    void test_invalidProtectionResult(ProtectedConsumer reply) {
        var before = checker.snapshot(orderId());
        protection.mockResult(details, reply);
        invoke(template().build()).assertProblem(502, CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());
        assertEquals(before, checker.snapshot(orderId()));
        protection.assertCalls(details, 1);
    }

    /** 明确返回异常 DTO，不能依赖未配置时默认返回 null。 */
    static Stream<Arguments> invalidProtectionResults() {
        return Stream.of(Arguments.of((Object)null), Arguments.of(new ProtectedConsumer(null, "p")),
            Arguments.of(new ProtectedConsumer(" ", "p")), Arguments.of(new ProtectedConsumer("e".repeat(129), "p")),
            Arguments.of(new ProtectedConsumer("e", null)), Arguments.of(new ProtectedConsumer("e", " ")),
            Arguments.of(new ProtectedConsumer("e", "p".repeat(129))));
    }

    /** 保护期间的订单、会话、法律接受竞争由真实 SQL 拦截，不写入资料。 */
    @ParameterizedTest
    @ValueSource(strings = {"orderVersion", "sessionVersion", "accepted", "expired"})
    void test_changesDuringProtection(String change) {
        var afterRace = new AtomicReference<JsonNode>();
        protection.observe(request -> {
            switch (change) {
                case "orderVersion" -> orders.changeVersion(orderId());
                case "sessionVersion" -> acceptances.advanceSessionVersion(orderId());
                case "accepted" -> acceptances.mockAcceptances(orderId(), adjustableUtcClock.instant());
                case "expired" -> adjustableUtcClock.advance(Duration.ofHours(1));
                default -> throw new AssertionError("未定义的竞争场景");
            }
            afterRace.set(checker.snapshot(orderId()));
        });
        var response = exchange(fixture.getSession().getPublicToken(), template().build());
        response.assertProblem(change.equals("expired") ? 410 : 409, change.equals("expired")
            ? CustomerCheckoutErrorCode.CHECKOUT_EXPIRED.getCode() : CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assertEquals(afterRace.get(), checker.snapshot(orderId()));
        protection.assertCalls(details, 1);
        assertNoOtherExternalCalls();
    }

    /** 两个真实 HTTP 保存读到同一旧版本时只能提交一个，失败者不能覆盖成功者。 */
    @Test
    void test_concurrentSaves() throws Exception {
        var barrier = new CyclicBarrier(2);
        protection.observe(request -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError("并发保存未同时到达保护阶段", failure);
            }
        });
        var before = orderChecker.order(orderId());
        var unrelated = checker.unchangedFacts(orderId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor
                .submit(() -> httpTestInvoker.exchange(HttpMethod.PUT, path(fixture.getSession().getPublicToken()),
                    TestRequestHeaders.customer(REQUEST_ID), template().build()));
            var second = executor
                .submit(() -> httpTestInvoker.exchange(HttpMethod.PUT, path(fixture.getSession().getPublicToken()),
                    TestRequestHeaders.customer(REQUEST_ID), template().build()));
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), results.stream().map(r -> r.getStatus().value()).sorted().toList());
            for (var result : results) {
                var response = new SaveConsumerResponseChecker(result, objectMapper, REQUEST_ID);
                if (result.getStatus().value() == 200) {
                    response.assertSaved(before.getOrderNo());
                } else {
                    response.assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
                }
            }
        }
        checker.assertSaved(orderId(), details, protectedValues, 1, adjustableUtcClock.instant(),
            adjustableUtcClock.instant());
        checker.assertOrderChangedOnlyVersion(before, adjustableUtcClock.instant());
        assertEquals(unrelated, checker.unchangedFacts(orderId()));
        protection.assertCalls(details, 2);
        assertNoOtherExternalCalls();
    }

    /** 失败入口检查完整快照和零外部调用。 */
    private void reject(Object body, int status, String code) {
        var before = checker.snapshot(orderId());
        invoke(body).assertProblem(status, code);
        assertEquals(before, checker.snapshot(orderId()));
        protection.assertNoCalls();
    }

    /** 普通场景复用当前 Checkout。 */
    private SaveConsumerResponseChecker invoke(Object body) {
        return invoke(fixture.getSession().getPublicToken(), body);
    }

    /** 每次 HTTP 都检查无关事实与下游零副作用。 */
    private SaveConsumerResponseChecker invoke(String token, Object body) {
        var before = checker.unchangedFacts(orderId());
        var response = exchange(token, body);
        assertEquals(before, checker.unchangedFacts(orderId()));
        assertNoOtherExternalCalls();
        return response;
    }

    /** 请求不携带 Client 身份或 Idempotency-Key，走正式消费者入口。 */
    private SaveConsumerResponseChecker exchange(String token, Object body) {
        return new SaveConsumerResponseChecker(
            httpTestInvoker.exchange(HttpMethod.PUT, path(token), TestRequestHeaders.customer(REQUEST_ID), body),
            objectMapper, REQUEST_ID);
    }

    /** 保存资料不触发商业准备、支付或取消。 */
    private void assertNoOtherExternalCalls() {
        preparationMocker.assertNoCalls();
        assertTrue(intents.calls().isEmpty());
        assertTrue(cards.calls().isEmpty());
        terminations.assertNoCalls();
    }

    /** 当前测试唯一登记的目标订单。 */
    private long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 只拼装正式资源路径，不添加业务控制参数。 */
    private String path(String token) {
        return "/customer-gateway-api/v1/checkouts/" + token + "/consumer";
    }
}
