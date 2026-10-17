package com.paytech.commerce.auto.ext.http.clientapi.v1.refund.get;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.client.api.v1.refund.ClientRefundApi;
import com.paytech.commerce.domain.enums.RefundRequestOrigin;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.checker.refund.GetRefundResponseChecker;
import com.paytech.commerce.test.checker.refund.RefundQueryChecker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.commerce.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 公开退款号查询：当前表示、Client 归属、参数与损坏事实，以及 GET 的零写入。
 *
 * @see ClientRefundApi#getRefund(long, long, String)
 */
class GetRefundTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.GetRefund.Test";
    @Autowired
    private OrderQueryDataMocker orderData;
    @Autowired
    private RefundCreationDataMocker refundData;
    @Autowired
    private RefundQueryChecker queryChecker;
    private Fixture fixture;

    /** 每例构造独立订单，方法内只覆盖场景差异，再由 Mocker 入库。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 全部状态保留当前原因、微秒时刻和版本；未决任务不能被查询推进。 */
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

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** Operations 来源公开业务号为空，内部原因说明不进入正式响应。 */
    @Test
    void test_operationsRefund() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).requestOrigin(RefundRequestOrigin.OPERATIONS).clientRefundId(null)
            .reasonCode("OPERATIONS_REQUESTED").reasonSummary("Internal operations note").build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 查询只需要退款和订单号，Checkout 与 Direct Pay 均不依赖会话或金额投影。 */
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

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 数据库金额按币种主单位输出，不使用生产计算器生成期望值。 */
    @ParameterizedTest
    @CsvSource({"USD,1.2,1.20", "USD,0.01,0.01", "JPY,1,1", "KWD,1.234,1.234"})
    void test_currencyFormatting(String currency, String amount, String expected) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        orderData.mock(fixture);
        var row = refundData.template(fixture).refundAmount(new BigDecimal(amount)).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), expected);
    }

    /** 每次 GET 都返回最新已提交退款，不回放先前查询的状态。 */
    @Test
    void test_currentStateOnRepeatedQueries() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).refundStatus(RefundStatus.PROCESSING).build();
        refundData.mock(row);
        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");

        row.setRefundStatus(RefundStatus.SUCCEEDED);
        row.setChannelRefundId(row.getRefundRequestId());
        row.setCompletedAt(now().plusSeconds(10));
        row.setUpdateTime(now().plusSeconds(10));
        row.setVersion(2);
        refundData.updateState(row);

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 同一订单多笔退款、其他订单和其他 Client 的同业务号不能干扰公开号定位。 */
    @Test
    void test_exactRefundAndClientScope() {
        orderData.mock(fixture);
        var own = refundData.template(fixture).clientRefundId("same-client-reference").build();
        refundData.mock(own);
        refundData.mock(refundData.template(fixture).build());
        var otherOrder = orderData.template().paid().clientOrderId("other-order").build();
        orderData.mock(otherOrder);
        var other = refundData.template(otherOrder).build();
        refundData.mock(other);
        var foreignOrder = orderData.template().paid().clientId(checkoutDataMocker.otherClientId()).build();
        orderData.mock(foreignOrder);
        var foreign = refundData.template(foreignOrder).clientRefundId(own.getClientRefundId()).build();
        refundData.mock(foreign);

        invoke(own.getRefundRequestNo()).assertRefund(own, fixture.getOrder().getOrderNo(), "10.00");
        invoke(other.getRefundRequestNo()).assertRefund(other, otherOrder.getOrder().getOrderNo(), "10.00");
        invoke(foreign.getRefundRequestNo()).assertProblem(404, "REFUND_NOT_FOUND");
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", own.getRefundRequestNo()).assertProblem(404,
            "REFUND_NOT_FOUND");
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", foreign.getRefundRequestNo())
            .assertRefund(foreign, foreignOrder.getOrder().getOrderNo(), "10.00");
    }

    /** 退款的 Client 相符仍必须检查关联订单归属，不泄露其他 Client 的订单号。 */
    @Test
    void test_refundWithForeignOrder() {
        fixture.getOrder().setClientId(checkoutDataMocker.otherClientId());
        orderData.mock(fixture);
        var row = refundData.template(fixture).clientId(checkoutDataMocker.clientId()).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertProblem(404, "REFUND_NOT_FOUND");
    }

    /** 孤立退款统一返回不存在；仍纳入快照与全局清理，不依赖数据库外键。 */
    @Test
    void test_refundWithMissingOrder() {
        var row = refundData.template(fixture).build();
        refundData.mock(row);
        refundData.mockPendingTask(row);

        invoke(row.getRefundRequestNo()).assertProblem(404, "REFUND_NOT_FOUND");
    }

    /** 未知公开号与错误大小写都不能找到退款，也不把 Client 业务号当作公开号。 */
    @ParameterizedTest
    @ValueSource(strings = {"unknown", "case", "client-reference"})
    void test_refundNumberMatching(String scenario) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);
        String refundNo = switch (scenario) {
            case "case" -> row.getRefundRequestNo().replace("rfd_", "RFD_");
            case "client-reference" -> row.getClientRefundId();
            default -> "rfd_missing";
        };

        invoke(refundNo).assertProblem(404, "REFUND_NOT_FOUND");
    }

    /** 公开号不按某一代生成器格式解析，只遵循现有不透明标识的长度约束。 */
    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void test_refundNumberLengthBoundaries(int length) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).refundRequestNo("R".repeat(length)).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 空白及超长路径参数由真实 HTTP 入口校验。 */
    @ParameterizedTest
    @ValueSource(strings = {" ", "too-long"})
    void test_invalidRefundNumber(String scenario) {
        orderData.mock(fixture);
        invoke("too-long".equals(scenario) ? "x".repeat(65) : scenario).assertProblem(400, "INVALID_REQUEST");
    }

    /** 缺失、非正数、非数字和溢出的可信身份头都不能进入业务查询。 */
    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        refundData.mock(row);

        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            row.getRefundRequestNo()).assertProblem(400, "INVALID_REQUEST");
    }

    /** 两种身份头使用同一组协议负例，不修改公共 Header 工厂的默认行为。 */
    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }

    /** 现行响应版本的整数上界可以精确展示，不截断或翻转。 */
    @Test
    void test_maximumRepresentationVersion() {
        orderData.mock(fixture);
        var row = refundData.template(fixture).version(Integer.MAX_VALUE).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertRefund(row, fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 损坏的持久化事实返回安全系统错误，不输出成功 DTO 或改写退款数据。 */
    @ParameterizedTest
    @ValueSource(strings = {"zero-amount", "negative-amount", "zero-version", "version-overflow",
        "missing-client-reference", "operations-reference", "update-before-create", "nonterminal-completed",
        "terminal-incomplete", "completed-before-create", "completed-after-update", "missing-channel", "zero-channel"})
    void test_invalidStoredFacts(String scenario) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).build();
        switch (scenario) {
            case "zero-amount" -> row.setRefundAmount(BigDecimal.ZERO);
            case "negative-amount" -> row.setRefundAmount(new BigDecimal("-1"));
            case "zero-version" -> row.setVersion(0);
            case "version-overflow" -> row.setVersion((long)Integer.MAX_VALUE + 1);
            case "missing-client-reference" -> row.setClientRefundId(null);
            case "operations-reference" -> row.setRequestOrigin(RefundRequestOrigin.OPERATIONS);
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

        invoke(row.getRefundRequestNo()).assertInternalError();
    }

    /** 存量金额精度或币种损坏是服务端事实问题，不能归类为 Client 请求参数错误。 */
    @ParameterizedTest(name = "currency={0}, storedAmount={1}")
    @CsvSource({"USD,0.001", "ZZZ,1"})
    void test_invalidStoredMoney(String currency, String amount) {
        orderData.mock(fixture);
        var row = refundData.template(fixture).currency(currency).refundAmount(new BigDecimal(amount)).build();
        refundData.mock(row);

        invoke(row.getRefundRequestNo()).assertInternalError();
    }

    /** GET 没有请求体，复用公共 HTTP 调用器，不新增专用测试基类。 */
    private GetRefundResponseChecker invoke(String refundNo) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", refundNo);
    }

    /** 每次 HTTP 调用都比较两侧 Client 的全列快照，并确认没有 CCT 调用。 */
    private GetRefundResponseChecker invoke(String client, String credential, String refundNo) {
        var before = queryChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var uri = UriComponentsBuilder.fromPath("/client-gateway-api/v1/refunds/{refundRequestNo}")
            .buildAndExpand(refundNo).toUriString();
        var result = httpTestInvoker.get(uri, TestRequestHeaders.client(REQUEST_ID, client, credential));
        queryChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        return new GetRefundResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 使用全局每例重置的 UTC 时钟构造确定性前置时刻。 */
    private Instant now() {
        return adjustableUtcClock.instant();
    }
}
