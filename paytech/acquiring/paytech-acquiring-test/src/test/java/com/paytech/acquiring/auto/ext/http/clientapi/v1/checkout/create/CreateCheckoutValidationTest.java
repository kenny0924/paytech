package com.paytech.commerce.auto.ext.http.clientapi.v1.checkout.create;

import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody.CheckoutItemInfo;
import static com.paytech.commerce.test.builder.checkout.CreateCheckoutRequestBuilder.item;
import static com.paytech.commerce.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.commerce.client.api.v1.checkout.ClientCheckoutApi;
import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.commerce.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.commerce.test.builder.external.CommercialFactsBuilder;
import com.paytech.commerce.test.builder.checkout.CreateCheckoutRequestBuilder;
import com.paytech.foundation.common.http.ApiHeaderNames;

/**
 * 请求绑定、字段与金额校验及提前拒绝的零副作用。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutValidationTest extends AbstractCreateCheckoutApiTest {
    /** 第二层 BeforeEach：仅准备本类各场景共用的 CCT 预检 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 普通字段和集合校验在外部查询与写入之前完成。 */
    @ParameterizedTest
    @MethodSource("invalidRequests")
    void test_invalidRequest(Consumer<CreateCheckoutRequestBuilder> change, String field) {
        var builder = template();
        change.accept(builder);
        invoke(builder.build()).assertProblem(422, "VALIDATION_ERROR").assertFieldError(field);
        assertNoBusinessEffects();
    }

    /** 当前 HTTP 合同要求可信身份为正整数；绑定失败不能进入业务。 */
    @ParameterizedTest
    @MethodSource("invalidTrustedIdentities")
    void test_invalidTrustedIdentity(String headerName, String value) {
        String client = ApiHeaderNames.GATEWAY_CLIENT_ID.equals(headerName) ? value : Long.toString(clientId());
        String credential = ApiHeaderNames.GATEWAY_CREDENTIAL_ID.equals(headerName) ? value : "1";
        invoke(client, credential, template().build()).assertProblem(400, "INVALID_REQUEST");
        assertNoBusinessEffects();
    }

    /** 金额关系或币种精度不合法时，不做下游查询或写入。 */
    @ParameterizedTest
    @CsvSource({"1,0.001,0", "0.5,0.01,0", "1,10,10.01", "2,9999999999999999.99,0"})
    void test_invalidAmounts(String quantity, String unit, String discount) {
        invoke(template().items(List.of(item("line-1", "Item", quantity, unit, discount))).build()).assertProblem(422,
            "VALIDATION_ERROR");
        assertNoBusinessEffects();
    }

    /** 各行合法但汇总超过数据库范围时也必须提前拒绝。 */
    @Test
    void test_aggregateAmountOverflow() {
        invoke(template().items(List.of(item("a", "First", "1", "6000000000000000", null),
            item("b", "Second", "1", "6000000000000000", null))).build()).assertProblem(422, "VALIDATION_ERROR");
        assertNoBusinessEffects();
    }

    /** 精确边界外一微秒也不能放行，避免仅测试整数分钟的粗边界。 */
    @ParameterizedTest
    @ValueSource(longs = {299999999, 86400000001L})
    void test_expiryOutsideBoundary(long microseconds) {
        invoke(template().expiresAt(NOW.plusNanos(microseconds * 1000)).build()).assertProblem(422, "VALIDATION_ERROR");
        assertNoBusinessEffects();
    }

    /** HTTP 原始 JSON 不允许把数字金额强制转为字符串。 */
    @Test
    void test_numericAmountIsNotCoerced() {
        var json = objectMapper.valueToTree(template().build());
        ((tools.jackson.databind.node.ObjectNode)json.path("items").get(0)).put("unitAmount", 19.95);
        invoke(json.toString()).assertProblem(400, "INVALID_REQUEST");
        assertNoBusinessEffects();
    }

    /** 可选字段必须省略，不能通过显式 null 绕过 Wire 校验。 */
    @ParameterizedTest
    @ValueSource(strings = {"expiresAt", "description", "discountAmount"})
    void test_explicitNullOptionalField(String field) {
        var json = (tools.jackson.databind.node.ObjectNode)objectMapper.valueToTree(template().build());
        var target =
            "expiresAt".equals(field) ? json : (tools.jackson.databind.node.ObjectNode)json.path("items").get(0);
        target.putNull(field);
        invoke(json.toString()).assertProblem(400, "INVALID_REQUEST");
        assertNoBusinessEffects();
    }

    /** 损坏 JSON 在业务入口前失败。 */
    @Test
    void test_malformedJson() {
        invoke("{\"clientOrderId\":").assertProblem(400, "INVALID_REQUEST");
        assertNoBusinessEffects();
    }

    /** 当前接口只启用 USD，其他合法币种也应在预检和保存前拒绝。 */
    @Test
    void test_unsupportedCurrencyIsRejectedBeforePersistence() {
        preparationMocker.mockFacts(clientId(), "US", "EUR", CommercialFactsBuilder.template().build());
        var response = invoke(template().currency("EUR").build());
        assertAll(() -> response.assertProblem(422, "VALIDATION_ERROR").assertFieldError("currency"),
            this::assertNoBusinessEffects);
    }

    /** 商品行身份重复应作为请求错误拒绝，而不是等唯一索引抛系统异常。 */
    @Test
    void test_duplicateItemIdsAreRejectedBeforePersistence() {
        var response = invoke(template()
            .items(List.of(item("same", "First", "1", "1", null), item("same", "Second", "1", "1", null))).build());
        assertAll(() -> response.assertProblem(422, "VALIDATION_ERROR"), this::assertNoBusinessEffects);
    }

    /** 合法默认值上的单一字段变更，各参数用例独立执行 BeforeEach/AfterEach。 */
    private static Stream<Arguments> invalidRequests() {
        return Stream.of(invalid(builder -> builder.clientOrderId(null), "clientOrderId"),
            invalid(builder -> builder.clientOrderId(""), "clientOrderId"),
            invalid(builder -> builder.clientOrderId("has space"), "clientOrderId"),
            invalid(builder -> builder.clientOrderId("x".repeat(129)), "clientOrderId"),
            invalid(builder -> builder.currency(null), "currency"),
            invalid(builder -> builder.currency("usd"), "currency"),
            invalid(builder -> builder.consumerCountryCode("usa"), "consumerCountryCode"),
            invalid(builder -> builder.items(null), "items"), invalid(builder -> builder.items(List.of()), "items"),
            invalid(builder -> builder.items(Arrays.asList(
                (CheckoutItemInfo)null)),
                "items[0]"),
            invalid(
                builder -> builder
                    .items(IntStream.range(0, 101).mapToObj(i -> item("item-" + i, "Item", "1", "1", null)).toList()),
                "items"),
            invalid(builder -> builder.items(List.of(item("line", "", "1", "1", null))), "items[0].name"),
            invalid(builder -> builder.items(List.of(item("line", "Item", "0", "1", null))), "items[0].quantity"),
            invalid(builder -> builder.items(List.of(item("line", "Item", "1", "-1", null))), "items[0].unitAmount"),
            invalid(builder -> builder.items(List.of(item("line", "Item", "1", "1e2", null))), "items[0].unitAmount"));
    }

    /** 缺失、非正数、非法文本和 long 溢出均从真实 Header 绑定进入。 */
    private static Stream<Arguments> invalidTrustedIdentities() {
        return Stream.of(ApiHeaderNames.GATEWAY_CLIENT_ID, ApiHeaderNames.GATEWAY_CREDENTIAL_ID)
            .flatMap(header -> Stream.of(null, "0", "-1", "invalid-id", "9223372036854775808")
                .map(value -> Arguments.of(header, value)));
    }

    /** 为参数化测试保留类型明确的请求变更。 */
    private static Arguments invalid(Consumer<CreateCheckoutRequestBuilder> change, String field) {
        return Arguments.of(change, field);
    }

    /** 提前拒绝必须同时没有下游调用和本地订单。 */
    private void assertNoBusinessEffects() {
        checkoutChecker.assertOrderCount(clientId(), otherClientId(), 0);
        preparationMocker.assertNoCalls();
    }
}
