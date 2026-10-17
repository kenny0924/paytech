package com.paytech.commerce.auto.ext.http.clientapi.v1.refund.refundableamount;

import static com.paytech.commerce.test.builder.order.GetOrderDataBuilder.decimal;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.client.api.v1.refund.ClientRefundableAmountApi;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.checker.refund.GetRefundableAmountResponseChecker;
import com.paytech.commerce.test.checker.refund.RefundQueryChecker;
import com.paytech.commerce.test.mocker.external.CheckoutCardMocker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.commerce.test.mocker.external.PaymentTerminationMocker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.commerce.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 可退款金额查询：金额位置、币种、归属及参数边界，所有 GET 均检查数据库零写入。
 *
 * @see ClientRefundableAmountApi#getRefundableAmount(long, long, String)
 */
class GetRefundableAmountTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.GetRefundableAmount.Test";
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

    /** 每例构造独立的已支付订单，差异由具体用例调整后插入真实数据库。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 基数扣除预占和成功退款；完全占用、完全退款及组合耗尽均为零。 */
    @ParameterizedTest
    @CsvSource({"50,0,0,50.00", "50,12.30,7.80,29.90", "50,50,0,0.00", "50,0,50,0.00", "50,20,30,0.00",
        "0.30,0.10,0.10,0.10"})
    void test_availableBalance(String base, String reserved, String refunded, String expected) {
        fixture.getRefund().setRefundableBaseAmount(decimal(base));
        fixture.getRefund().setReservedAmount(decimal(reserved));
        fixture.getRefund().setRefundedAmount(decimal(refunded));
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), expected);
    }

    /** 订单状态不限制只读查询；未支付零额与支付后的可退基数都取本地位置。 */
    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void test_orderStatuses(OrderStatus status) {
        if (status != OrderStatus.PAID) {
            fixture = orderData.template().build();
        }
        fixture.getOrder().setOrderStatus(status);
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(),
            status == OrderStatus.PAID ? "50.00" : "0.00");
    }

    /** Checkout 和 Direct Pay 都只需要订单及位置，不要求 Session、商品或其他投影。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_onlyOrderAndPositionRequired(boolean directPay) {
        if (directPay) {
            fixture = orderData.template().paid().directPay().build();
        }
        fixture.setSession(null);
        fixture.setPayment(null);
        fixture.setFunds(null);
        fixture.setItems(List.of());
        fixture.setContextCountry(null);
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");
    }

    /** 使用主单位字符串及币种精度，包含最小金额、零额和数据库整数位上界。 */
    @ParameterizedTest
    @CsvSource({"USD,1.2,1.20", "USD,0.01,0.01", "USD,9999999999999999.99,9999999999999999.99", "JPY,1,1", "JPY,0,0",
        "KWD,1.234,1.234", "KWD,0,0.000"})
    void test_currencyFormatting(String currency, String base, String expected) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        fixture.getRefund().setRefundableBaseAmount(decimal(base));
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), expected);
    }

    /** 时间与版本来自同一位置行；过期后重复读取也不刷新时点或预占金额。 */
    @Test
    void test_positionSnapshotSurvivesExpiryAndRepeatedReads() {
        fixture.getRefund().setUpdateTime(adjustableUtcClock.instant().minusSeconds(10).plusNanos(123000));
        fixture.getRefund().setVersion(1);
        orderData.mock(fixture);
        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");

        adjustableUtcClock.advance(Duration.ofHours(1));

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");
    }

    /** 后续 GET 读取新提交的预占、已退款、时点与版本，不重放上一次查询结果。 */
    @Test
    void test_currentPositionAfterUpdate() {
        orderData.mock(fixture);
        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");

        fixture.getRefund().setReservedAmount(decimal("30"));
        fixture.getRefund().setRefundedAmount(decimal("10"));
        fixture.getRefund().setUpdateTime(adjustableUtcClock.instant().plusSeconds(5));
        fixture.getRefund().setVersion(10);
        orderData.updateRefundPosition(fixture.getRefund());

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "10.00");
    }

    /** 退款申请已计入位置，不再重复扣减；未决提交任务不能由查询推进。 */
    @Test
    void test_refundRequestsAreNotDeductedTwice() {
        fixture.getRefund().setReservedAmount(decimal("10"));
        fixture.getRefund().setRefundedAmount(decimal("5"));
        orderData.mock(fixture);
        var pending = refundData.template(fixture).refundStatus(RefundStatus.PROCESSING).build();
        refundData.mock(pending);
        refundData.mockPendingTask(pending);
        var succeeded = refundData.template(fixture).refundAmount(decimal("5")).refundStatus(RefundStatus.SUCCEEDED)
            .channelRefundId(101L).completedAt(adjustableUtcClock.instant()).build();
        refundData.mock(succeeded);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "35.00");
    }

    /** 按订单号和 Client 精确取位置，不混用同 Client 的其他订单或跨 Client 同业务号。 */
    @Test
    void test_orderAndClientScope() {
        orderData.mock(fixture);
        var other = orderData.template().paid().clientOrderId("other-order").build();
        other.getRefund().setReservedAmount(decimal("20"));
        orderData.mock(other);
        var foreign = orderData.template().paid().clientId(checkoutDataMocker.otherClientId()).build();
        foreign.getRefund().setRefundedAmount(decimal("10"));
        orderData.mock(foreign);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");
        invoke(other.getOrder().getOrderNo()).assertAmount(other.getOrder().getOrderNo(), other.getRefund(), "30.00");
        invoke(foreign.getOrder().getOrderNo()).assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", fixture.getOrder().getOrderNo())
            .assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", foreign.getOrder().getOrderNo())
            .assertAmount(foreign.getOrder().getOrderNo(), foreign.getRefund(), "40.00");
    }

    /** 未知、大小写不匹配或误用 Client 业务编号都返回订单不存在。 */
    @ParameterizedTest
    @ValueSource(strings = {"unknown", "case", "client-reference"})
    void test_orderNumberMatching(String scenario) {
        orderData.mock(fixture);
        var orderNo = switch (scenario) {
            case "case" -> fixture.getOrder().getOrderNo().replace("ord_", "ORD_");
            case "client-reference" -> fixture.getOrder().getClientOrderId();
            default -> "ord_missing";
        };

        invoke(orderNo).assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** 身份隔离先于位置校验，其他 Client 的损坏数据也不能通过错误类型泄露。 */
    @Test
    void test_foreignOrderWithMissingPosition() {
        fixture.getOrder().setClientId(checkoutDataMocker.otherClientId());
        fixture.setRefund(null);
        orderData.mock(fixture);

        invoke().assertProblem(404, CommerceCheckoutErrorCode.ORDER_NOT_FOUND.getCode());
    }

    /** 不透明订单号仅遵守长度合同，不强制某一代编号格式。 */
    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void test_orderNumberLengthBoundaries(int length) {
        fixture.getOrder().setOrderNo("R".repeat(length));
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");
    }

    /** 空白和超过最大长度的路径值由真实 HTTP 参数校验拒绝。 */
    @ParameterizedTest
    @ValueSource(strings = {" ", "too-long"})
    void test_invalidOrderNumber(String scenario) {
        orderData.mock(fixture);

        invoke("too-long".equals(scenario) ? "x".repeat(65) : scenario).assertProblem(400,
            CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 两个可信身份头均检查缺失、非正数、非数字及溢出。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);

        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            fixture.getOrder().getOrderNo()).assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 共用同组协议负例，Header 工厂保持原值，不补齐缺失参数。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }

    /** 退款位置版本在现有响应的整数上界仍精确输出。 */
    @Test
    void test_maximumRepresentationVersion() {
        fixture.getRefund().setVersion(Integer.MAX_VALUE);
        orderData.mock(fixture);

        invoke().assertAmount(fixture.getOrder().getOrderNo(), fixture.getRefund(), "50.00");
    }

    /** 缺失、币种冲突、负额、超额预占及非法版本均为服务端事实错误。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing", "currency-mismatch", "negative-base", "negative-reserved", "negative-refunded",
        "reserved-exceeds-base", "refunded-exceeds-base", "sum-exceeds-base", "zero-version", "version-overflow"})
    void test_invalidStoredPosition(String scenario) {
        var position = fixture.getRefund();
        switch (scenario) {
            case "missing" -> fixture.setRefund(null);
            case "currency-mismatch" -> position.setCurrency("EUR");
            case "negative-base" -> position.setRefundableBaseAmount(decimal("-1"));
            case "negative-reserved" -> position.setReservedAmount(decimal("-1"));
            case "negative-refunded" -> position.setRefundedAmount(decimal("-1"));
            case "reserved-exceeds-base" -> position.setReservedAmount(decimal("51"));
            case "refunded-exceeds-base" -> position.setRefundedAmount(decimal("51"));
            case "sum-exceeds-base" -> {
                position.setReservedAmount(decimal("30"));
                position.setRefundedAmount(decimal("21"));
            }
            case "zero-version" -> position.setVersion(0);
            case "version-overflow" -> position.setVersion((long)Integer.MAX_VALUE + 1);
            default -> throw new IllegalArgumentException(scenario);
        }
        orderData.mock(fixture);

        invoke().assertInternalError();
    }

    /** 不只校验最终余额精度；各组成金额损坏也必须返回系统错误。 */
    @ParameterizedTest
    @CsvSource({"USD,50.001,0.001,0", "USD,50,0.001,0", "USD,50,0,0.001", "ZZZ,50,0,0"})
    void test_invalidStoredMoney(String currency, String base, String reserved, String refunded) {
        fixture.getOrder().setCurrency(currency);
        fixture.getRefund().setCurrency(currency);
        fixture.getRefund().setRefundableBaseAmount(decimal(base));
        fixture.getRefund().setReservedAmount(decimal(reserved));
        fixture.getRefund().setRefundedAmount(decimal(refunded));
        orderData.mock(fixture);

        invoke().assertInternalError();
    }

    /** 默认查询本例当前 Client 的订单，不构造无意义的 GET 请求体。 */
    private GetRefundableAmountResponseChecker invoke() {
        return invoke(fixture.getOrder().getOrderNo());
    }

    /** 编号场景复用同一真实 HTTP 调用链。 */
    private GetRefundableAmountResponseChecker invoke(String orderNo) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo);
    }

    /** 全列快照检查查询零写入，外部调用记录检查没有重新准备或支付。 */
    private GetRefundableAmountResponseChecker invoke(String client, String credential, String orderNo) {
        var before = queryChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var uri = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/refundable-amount")
            .buildAndExpand(orderNo).toUriString();
        var result = httpTestInvoker.get(uri, TestRequestHeaders.client(REQUEST_ID, client, credential));
        queryChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        preparationMocker.assertNoCalls();
        assertTrue(intentMocker.calls().isEmpty(), "查询不应创建 Intent");
        assertTrue(cardMocker.calls().isEmpty(), "查询不应提交支付");
        terminationMocker.assertNoCalls();
        return new GetRefundableAmountResponseChecker(result, objectMapper, REQUEST_ID);
    }
}
