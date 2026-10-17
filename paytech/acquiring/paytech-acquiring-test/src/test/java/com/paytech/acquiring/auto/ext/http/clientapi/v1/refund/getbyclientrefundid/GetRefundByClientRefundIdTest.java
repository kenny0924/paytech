package com.paytech.acquiring.auto.ext.http.clientapi.v1.refund.getbyclientrefundid;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.decimal;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.refund.ClientRefundApi;
import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.checker.refund.GetRefundResponseChecker;
import com.paytech.acquiring.test.checker.refund.RefundQueryChecker;
import com.paytech.acquiring.test.mocker.external.CheckoutCardMocker;
import com.paytech.acquiring.test.mocker.external.CheckoutIntentMocker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.acquiring.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * Client 业务退款号查询：来源和归属隔离、Query 编码、当前详情及本地查询零写入。
 *
 * @see ClientRefundApi#getRefundByClientRefundId(long, long, String)
 */
class GetRefundByClientRefundIdTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.GetRefundByClientRefundId.Test";
    @Autowired
    private OrderQueryDataMocker orderData;
    @Autowired
    private RefundCreationDataMocker refundData;
    @Autowired
    private RefundQueryChecker queryChecker;
    @Autowired
    private CheckoutIntentMocker intentMocker;
    @Autowired
    private CheckoutCardMocker cardMocker;
    @Autowired
    private PaymentTerminationMocker terminationMocker;
    private Fixture fixture;

    /** 每例构造独立订单，方法内只调整当前场景，由 Mocker 直接插入前置数据。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 返回全部退款状态的当前表示，保留可空字段、微秒时刻及版本，不推进未决任务。 */
    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void test_refundStatuses(RefundStatus status) {
        orderData.mock(fixture);
        var row =
            refundData.template(fixture).refundStatus(status).version(7).reasonSummary("Internal refund fixture note")
                .createTime(now().minusSeconds(60).plusNanos(123000)).updateTime(now().plusNanos(456000)).build();
        if (status.terminal()) {
            row.setCompletedAt(now().minusSeconds(10).plusNanos(789000));
            row.setChannelRefundId(row.getRefundRequestId());
        }
        if (status == RefundStatus.FAILED || status == RefundStatus.CANCELLED) {
            row.setStatusReasonCode("CHANNEL_REJECTED");
        }
        refundData.mock(row);
        if (!status.terminal()) {
            refundData.mockPendingTask(row);
        }

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** Checkout 和 Direct Pay 都只需退款及订单身份，不依赖会话、商品或金额投影。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_withoutCheckoutOrProjections(boolean directPay) {
        if (directPay) {
            fixture = orderData.template().paid().directPay().build();
        }
        fixture.setSession(null);
        fixture.setPayment(null);
        fixture.setRefund(null);
        fixture.setFunds(null);
        fixture.setItems(List.of());
        fixture.setContextCountry(null);
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 金额按币种主单位和精度输出，期望直接给定，不复用生产 Money 计算。 */
    @ParameterizedTest
    @CsvSource({"USD,1.2,1.20", "USD,0.01,0.01", "JPY,1,1", "KWD,1.234,1.234"})
    void test_currencyFormatting(String currency, String amount, String expected) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        orderData.mock(fixture);
        var row = refundData.template(fixture).refundAmount(decimal(amount)).build();
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), expected);
    }

    /** 订单过期不影响退款查询，下一次 GET 应返回新提交的退款状态而非首次响应。 */
    @Test
    void test_currentStateOnRepeatedQueries() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).refundStatus(RefundStatus.PROCESSING).build();
        refundData.mock(row);
        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");

        adjustableUtcClock.advance(Duration.ofHours(1));
        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
        row.setRefundStatus(RefundStatus.SUCCEEDED);
        row.setChannelRefundId(row.getRefundRequestId());
        row.setCompletedAt(now());
        row.setUpdateTime(now());
        row.setVersion(2);
        refundData.updateState(row);

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 同 Client 多订单、多退款精确定位，不读取该订单的另一笔退款。 */
    @Test
    void test_exactRefundWithinClient() {
        orderData.mock(fixture);
        var first = refundData.template(fixture).clientRefundId("first-refund").build();
        refundData.mock(first);
        var second = refundData.template(fixture).clientRefundId("second-refund").refundAmount(decimal("7")).build();
        refundData.mock(second);
        var otherOrder = orderData.template().paid().clientOrderId("other-order").build();
        orderData.mock(otherOrder);
        var other = refundData.template(otherOrder).refundAmount(decimal("12")).build();
        refundData.mock(other);

        invoke(first.getClientRefundId()).assertRefund(first, fixture.getOrder().getOrderNo(), "10.00");
        invoke(second.getClientRefundId()).assertRefund(second, fixture.getOrder().getOrderNo(), "7.00");
        invoke(other.getClientRefundId()).assertRefund(other, otherOrder.getOrder().getOrderNo(), "12.00");
    }

    /** 不同 Client 可使用相同业务退款号，两个方向均只能返回自己的退款。 */
    @Test
    void test_sameReferenceAcrossClients() {
        orderData.mock(fixture);
        var own = refundData.template(fixture).clientRefundId("shared-reference").build();
        refundData.mock(own);
        var otherOrder = orderData.template().paid().clientId(checkoutDataMocker.otherClientId()).build();
        orderData.mock(otherOrder);
        var other =
            refundData.template(otherOrder).clientRefundId(own.getClientRefundId()).refundAmount(decimal("20")).build();
        refundData.mock(other);

        invoke(own.getClientRefundId()).assertRefund(own, fixture.getOrder().getOrderNo(), "10.00");
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", own.getClientRefundId()).assertRefund(other,
            otherOrder.getOrder().getOrderNo(), "20.00");
    }

    /** 未知业务号和仅属于其他 Client 的业务号均返回相同的不存在错误。 */
    @Test
    void test_unknownAndForeignRefund() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke("missing-refund").assertProblem(404, CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", row.getClientRefundId()).assertProblem(404,
            CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** 空命名空间返回单资源不存在，不返回空对象或列表。 */
    @Test
    void test_noRefunds() {
        invoke("missing-refund").assertProblem(404, CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** 业务号查询不能回退到公开退款号匹配。 */
    @Test
    void test_doesNotFallBackToRefundRequestNo() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertProblem(404, CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** Operations 正常没有业务号；即使存量错误填写该字段，也不能通过 CLIENT 查询命中。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_operationsOriginExcluded(boolean storedReference) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).requestOrigin(RefundRequestOrigin.OPERATIONS)
            .clientRefundId(storedReference ? "operations-refund" : null).build();
        refundData.mock(row);

        invoke(storedReference ? row.getClientRefundId() : row.getRefundRequestNo()).assertProblem(404,
            CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** 退款 Client 正确但关联订单不存在或归属其他 Client 时，也不能泄露退款详情。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_missingOrForeignOrder(boolean foreignOrder) {
        var row = refundData.template(fixture).build();
        if (foreignOrder) {
            fixture.getOrder().setClientId(checkoutDataMocker.otherClientId());
            orderData.mock(fixture);
        }
        refundData.mock(row);
        refundData.mockPendingTask(row);

        invoke(row.getClientRefundId()).assertProblem(404, CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** 大小写不同的业务编号可以共存，并精确匹配原字符串。 */
    @Test
    void test_caseSensitiveReference() {
        orderData.mock(fixture);
        var upper = refundData.template(fixture).clientRefundId("Case-Refund").build();
        refundData.mock(upper);
        var lower = refundData.template(fixture).clientRefundId("case-refund").refundAmount(decimal("5")).build();
        refundData.mock(lower);

        invoke(upper.getClientRefundId()).assertRefund(upper, fixture.getOrder().getOrderNo(), "10.00");
        invoke(lower.getClientRefundId()).assertRefund(lower, fixture.getOrder().getOrderNo(), "5.00");
        invoke("CASE-REFUND").assertProblem(404, CommerceCheckoutErrorCode.REFUND_NOT_FOUND.getCode());
    }

    /** Query 中的加号、百分号和保留字符必须只解码一次，不改变编号或注入其他参数。 */
    @ParameterizedTest
    @ValueSource(strings = {"Refund+123", "Refund&key=value", "Refund?x=1#fragment", "Refund%2F123", "Refund/123",
        "Refund{123}", "Refund\"123'", "Refund\\123", "!~"})
    void test_encodedReference(String clientRefundId) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).clientRefundId(clientRefundId).build();
        refundData.mock(row);

        invoke(clientRefundId).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 业务退款号允许 1 到 128 字符，不使用公开退款号的 64 字符约束。 */
    @ParameterizedTest
    @ValueSource(ints = {1, 128})
    void test_referenceLengthBoundaries(int length) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).clientRefundId("a".repeat(length)).build();
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 缺失、空白、非法字符和超长 Query 由真实入口校验，不能 trim 后命中原退款。 */
    @ParameterizedTest
    @MethodSource("invalidReferences")
    void test_invalidReference(String clientRefundId) {
        orderData.mock(fixture);
        refundData.mock(refundData.template(fixture).clientRefundId("query-refund").build());

        invoke(clientRefundId).assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 每个身份头单独验证缺失、空值、非正数、格式和溢出，不由另一字段提前失败。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            row.getClientRefundId()).assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 归属按 Client 隔离，同 Client 的其他合法凭据也能查询。 */
    @Test
    void test_positiveCredentialBoundary() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke(Long.toString(checkoutDataMocker.clientId()), Long.toString(Long.MAX_VALUE), row.getClientRefundId())
            .assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 当前表示版本的整数上界保持精确，不溢出为负数。 */
    @Test
    void test_maximumRepresentationVersion() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).version(Integer.MAX_VALUE).build();
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 损坏的金额、版本、时刻及终态事实返回系统错误，不修改原退款。 */
    @ParameterizedTest
    @ValueSource(strings = {"zero-amount", "negative-amount", "zero-version", "version-overflow",
        "update-before-create", "nonterminal-completed", "terminal-incomplete", "completed-before-create",
        "completed-after-update", "missing-channel", "zero-channel"})
    void test_invalidStoredFacts(String scenario) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        switch (scenario) {
            case "zero-amount" -> row.setRefundAmount(decimal("0"));
            case "negative-amount" -> row.setRefundAmount(decimal("-1"));
            case "zero-version" -> row.setVersion(0);
            case "version-overflow" -> row.setVersion((long)Integer.MAX_VALUE + 1);
            case "update-before-create" -> row.setUpdateTime(now().minusSeconds(1));
            case "nonterminal-completed" -> row.setCompletedAt(now());
            case "terminal-incomplete" -> row.setRefundStatus(RefundStatus.FAILED);
            case "completed-before-create" -> {
                row.setRefundStatus(RefundStatus.FAILED);
                row.setCompletedAt(now().minusSeconds(1));
            }
            case "completed-after-update" -> {
                row.setRefundStatus(RefundStatus.CANCELLED);
                row.setCompletedAt(now().plusSeconds(1));
            }
            case "missing-channel" -> {
                row.setRefundStatus(RefundStatus.SUCCEEDED);
                row.setCompletedAt(now());
            }
            case "zero-channel" -> row.setChannelRefundId(0L);
            default -> throw new IllegalArgumentException(scenario);
        }
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertInternalError();
    }

    /** 数据库币种或金额精度错误属于服务端事实问题，不能归类为 Client 请求错误。 */
    @ParameterizedTest
    @CsvSource({"USD,0.001", "ZZZ,1"})
    void test_invalidStoredMoney(String currency, String amount) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).currency(currency).refundAmount(decimal(amount)).build();
        refundData.mock(row);

        invoke(row.getClientRefundId()).assertInternalError();
    }

    /** 仅使用当前 Client 的身份头，不添加请求幂等 Key 或 GET 请求体。 */
    private GetRefundResponseChecker invoke(String clientRefundId) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", clientRefundId);
    }

    /** 复用已装配的 HTTP 客户端编码 Query；每次调用均检查数据库和外部调用零副作用。 */
    private GetRefundResponseChecker invoke(String clientId, String credentialId, String clientRefundId) {
        var before = queryChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var result = restTestClient.get().uri(builder -> {
            builder.path("/client-gateway-api/v1/refunds");
            if (clientRefundId == null) {
                return builder.build();
            }
            return builder.queryParam("clientRefundId", "{clientRefundId}").build(clientRefundId);
        }).headers(headers -> headers.addAll(TestRequestHeaders.client(REQUEST_ID, clientId, credentialId))).exchange()
            .expectBody().returnResult();
        queryChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        preparationMocker.assertNoCalls();
        assertTrue(intentMocker.calls().isEmpty(), "查询不应创建 Intent");
        assertTrue(cardMocker.calls().isEmpty(), "查询不应提交支付");
        terminationMocker.assertNoCalls();
        return new GetRefundResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 编号只允许可打印非空白 ASCII，保留非法原值供真实绑定和校验处理。 */
    private static Stream<String> invalidReferences() {
        return Stream.of(null, "", " ", " query-refund", "query-refund ", "query refund", "query\trefund",
            "query\nrefund", "query\u007Frefund", "退款", "a".repeat(129));
    }

    /** 分别覆盖 Client 和 Credential 的身份参数边界。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "", "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }

    /** 所有前置时刻使用每例重置的 UTC 时钟。 */
    private Instant now() {
        return adjustableUtcClock.instant();
    }
}
