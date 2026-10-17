package com.paytech.commerce.auto.ext.http.clientapi.v1.refund.list;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.client.api.v1.enums.OrderCreationMode;
import com.paytech.commerce.client.api.v1.refund.ClientRefundApi;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domain.enums.RefundRequestOrigin;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.checker.refund.ListOrderRefundsResponseChecker;
import com.paytech.commerce.test.checker.refund.RefundQueryChecker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.commerce.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/**
 * 订单退款列表：真实 HTTP 分页、游标归属、当前表示与只读副作用。
 *
 * @see ClientRefundApi#listOrderRefunds(long, long, String, int, String)
 */
class ListOrderRefundsTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.ListOrderRefunds.Test";
    @Autowired
    private OrderQueryDataMocker orderData;
    @Autowired
    private RefundCreationDataMocker refundData;
    @Autowired
    private RefundQueryChecker queryChecker;
    private Fixture fixture;

    /** 每例独立构造订单；特殊场景可调整前置事实后再入库。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orderData.template().paid().build();
    }

    /** 空订单仍返回完整列表结构，Direct Pay 不需要 Checkout Session。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_emptyList(boolean directPay) {
        if (directPay) {
            fixture.getOrder().setCreationMode(OrderCreationMode.DIRECT_PAY);
            fixture.setSession(null);
        }
        orderData.mock(fixture);
        invoke(null, null).assertPage(false);
    }

    /** 返回退款当前表示，公开字段按状态包含显式 null，原因原文及渠道内部坐标不透出。 */
    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void test_refundStatuses(RefundStatus status) {
        orderData.mock(fixture);
        var row = refund("state").refundStatus(status).version(7).reasonSummary("Internal fixture note")
            .createTime(now().minusSeconds(60)).updateTime(now()).build();
        if (status.terminal()) {
            row.setCompletedAt(now().minusSeconds(10));
            row.setChannelRefundId(row.getRefundRequestId());
        }
        if (status == RefundStatus.FAILED || status == RefundStatus.CANCELLED) {
            row.setStatusReasonCode("CHANNEL_REJECTED");
        }
        refundData.mock(row);
        if (!status.terminal()) {
            refundData.mockPendingTask(row);
        }
        invoke(null, null).assertPage(false, row.getRefundRequestNo()).assertRefund(row,
            fixture.getOrder().getOrderNo(), "10.00");
    }

    /** Operations 来源业务号为 null；列表不依赖支付、额度、商业上下文或 Session。 */
    @Test
    void test_operationsRefundWithoutCheckoutOrProjections() {
        fixture.getOrder().setCreationMode(OrderCreationMode.DIRECT_PAY);
        fixture.setSession(null);
        fixture.setPayment(null);
        fixture.setRefund(null);
        fixture.setFunds(null);
        fixture.setContextCountry(null);
        orderData.mock(fixture);
        var row = refund("operations").requestOrigin(RefundRequestOrigin.OPERATIONS).clientRefundId(null).build();
        refundData.mock(row);
        invoke(null, null).assertPage(false, row.getRefundRequestNo()).assertRefund(row,
            fixture.getOrder().getOrderNo(), "10.00");
    }

    @ParameterizedTest
    @CsvSource({"USD,1.2,1.20", "JPY,1,1", "KWD,1.234,1.234"})
    void test_currencyFormatting(String currency, String amount, String expected) {
        fixture = orderData.template().paid().currency(currency).build();
        fixture.getPayment().setCurrency(currency);
        orderData.mock(fixture);
        var row = refund("amount").refundAmount(new BigDecimal(amount)).build();
        refundData.mock(row);
        invoke(null, null).assertPage(false, row.getRefundRequestNo()).assertRefund(row,
            fixture.getOrder().getOrderNo(), expected);
    }

    /** 同一微秒按公开号的 ASCII 倒序，不使用内部主键、插入顺序或不区分大小写的排序。 */
    @Test
    void test_stablePaginationWithTiedMicrosecondTimestamps() {
        orderData.mock(fixture);
        var time = now().minusSeconds(20).plusNanos(123000);
        refundData.mock(refund("Z").createTime(time).build());
        refundData.mock(refund("A").createTime(time).build());
        refundData.mock(refund("newest").createTime(time.plusSeconds(1)).build());
        refundData.mock(refund("oldest").createTime(time.minusSeconds(1)).build());
        refundData.mock(refund("a").createTime(time).build());

        var first = invoke("2", null).assertPage(true, no("newest"), no("a")).getResponseBody();
        assertEquals(cursor(time, no("a")), first.getNextCursor());
        var second = invoke("2", first.getNextCursor()).assertPage(true, no("Z"), no("A")).getResponseBody();
        assertEquals(cursor(time, no("A")), second.getNextCursor());
        invoke("2", second.getNextCursor()).assertPage(false, no("oldest"));
        invoke("2", cursor(time.minusSeconds(1), no("oldest"))).assertPage(false);
    }

    /** 默认取 20 条，续页允许修改 limit，并且不跳过多取的第 21 条。 */
    @Test
    void test_defaultLimitAndDifferentContinuationLimit() {
        orderData.mock(fixture);
        for (int i = 1; i <= 21; i++) {
            refundData.mock(refund(String.format("%03d", i)).build());
        }
        var expected = new ArrayList<String>();
        for (int i = 21; i >= 2; i--) {
            expected.add(no(String.format("%03d", i)));
        }
        var first = invoke(null, null).assertPage(true, expected.toArray(String[]::new)).getResponseBody();
        invoke("100", first.getNextCursor()).assertPage(false, no("001"));
    }

    /** 边界页不能把“刚好填满”误判为还有下一页。 */
    @ParameterizedTest
    @CsvSource({"1,1,false", "2,1,true", "100,100,false", "101,100,true"})
    void test_pageSizeBoundaries(int total, int limit, boolean hasMore) {
        orderData.mock(fixture);
        for (int i = 1; i <= total; i++) {
            refundData.mock(refund(String.format("%03d", i)).build());
        }
        var expected = new ArrayList<String>();
        for (int i = total; i > total - limit; i--) {
            expected.add(no(String.format("%03d", i)));
        }
        invoke(Integer.toString(limit), null).assertPage(hasMore, expected.toArray(String[]::new));
    }

    /** 新插入的较新退款不挤入续页；已有退款状态变化在下一页可见，锚点状态变化不使游标失效。 */
    @Test
    void test_currentStateBetweenPagesWithoutOffsetDuplicates() {
        orderData.mock(fixture);
        var a = refund("A").build();
        var b = refund("B").build();
        var c = refund("C").build();
        refundData.mock(a);
        refundData.mock(b);
        refundData.mock(c);
        var first = invoke("1", null).assertPage(true, no("C")).getResponseBody();
        b.setRefundStatus(RefundStatus.SUCCEEDED);
        b.setChannelRefundId(b.getRefundRequestId());
        b.setCompletedAt(now());
        b.setVersion(3);
        refundData.updateState(b);
        c.setRefundStatus(RefundStatus.PROCESSING);
        c.setVersion(2);
        refundData.updateState(c);
        refundData.mock(refund("D").createTime(now().plusSeconds(1)).updateTime(now().plusSeconds(1)).build());

        invoke("2", first.getNextCursor()).assertPage(false, no("B"), no("A")).assertRefund(b,
            fixture.getOrder().getOrderNo(), "10.00");
    }

    /** 其他订单与其他 Client 的较新退款不能出现在当前列表。 */
    @Test
    void test_clientAndOrderScope() {
        orderData.mock(fixture);
        var own = refund("own").build();
        refundData.mock(own);
        var otherOrder = orderData.template().paid().clientOrderId("other-order").build();
        orderData.mock(otherOrder);
        refundData.mock(refundData.template(otherOrder).build());
        var otherClient = orderData.template().paid().clientId(checkoutDataMocker.otherClientId()).build();
        orderData.mock(otherClient);
        refundData.mock(refundData.template(otherClient).build());
        // 即使退款行的 Order 与 Client 坐标不一致，列表也必须同时限定两个维度。
        refundData.mock(refund("foreign-client").clientId(checkoutDataMocker.otherClientId()).build());

        invoke(null, null).assertPage(false, own.getRefundRequestNo());
        invoke(Long.toString(checkoutDataMocker.otherClientId()), "1", fixture.getOrder().getOrderNo(), null, null)
            .assertProblem(404, "ORDER_NOT_FOUND");
        invoke(Long.toString(checkoutDataMocker.clientId()), "1", otherClient.getOrder().getOrderNo(), null, null)
            .assertProblem(404, "ORDER_NOT_FOUND");
        invoke(Long.toString(checkoutDataMocker.clientId()), "1", "ord_missing", null, "invalid-cursor")
            .assertProblem(404, "ORDER_NOT_FOUND");
    }

    /** 合法编码也必须匹配同 Client、同 Order、原退款号和微秒创建时刻。 */
    @ParameterizedTest
    @ValueSource(strings = {"other-order", "other-client", "unknown", "case", "time"})
    void test_cursorMustMatchAnchor(String scenario) {
        orderData.mock(fixture);
        var anchor = refund("anchor").build();
        if (scenario.startsWith("other-")) {
            var other = orderData.template().paid().clientOrderId("cursor-owner").clientId(
                "other-client".equals(scenario) ? checkoutDataMocker.otherClientId() : checkoutDataMocker.clientId())
                .build();
            orderData.mock(other);
            anchor = refundData.template(other).build();
        }
        refundData.mock(anchor);
        String number = switch (scenario) {
            case "unknown" -> "rfd_missing";
            case "case" -> anchor.getRefundRequestNo().replace("rfd_", "RFD_");
            default -> anchor.getRefundRequestNo();
        };
        var time = "time".equals(scenario) ? anchor.getCreateTime().plusNanos(1000) : anchor.getCreateTime();
        invoke("1", cursor(time, number)).assertProblem(400, "INVALID_CURSOR");
    }

    /** 从 HTTP 入口验证版本、规范编码、字段分隔和微秒精度，不直接测试内部 decode。 */
    @ParameterizedTest
    @MethodSource("invalidCursors")
    void test_invalidCursor(String value) {
        orderData.mock(fixture);
        invoke("1", value).assertProblem(400, "INVALID_CURSOR");
    }

    private static Stream<String> invalidCursors() {
        var timestamp = "2026-09-08T00:00:00Z";
        return Stream.of("", " ", "invalid", "r2.YQ", "R1.YQ", "r1.***", "r1.", encoded("bad-time\nrfd_A"),
            encoded(timestamp), encoded(timestamp + "\nrfd_A\nextra"), encoded(timestamp + "\n"),
            encoded(timestamp + "\nwith space"), encoded(timestamp + "\n" + "a".repeat(65)),
            encoded("2026-09-08T00:00:00.000000001Z\nrfd_A"), encoded("2026-09-08T00:00:00+00:00\nrfd_A"),
            encoded(timestamp + "\nrfd_A") + "=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc", "2147483648"})
    void test_invalidLimit(String limit) {
        orderData.mock(fixture);
        invoke(limit, null).assertProblem(400, "INVALID_REQUEST");
    }

    @Test
    void test_oversizedCursorRejectedByBinding() {
        orderData.mock(fixture);
        invoke("1", "x".repeat(513)).assertProblem(400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void test_invalidIdentity(boolean clientHeader, String value) {
        orderData.mock(fixture);
        invoke(clientHeader ? value : Long.toString(checkoutDataMocker.clientId()), clientHeader ? "1" : value,
            fixture.getOrder().getOrderNo(), null, null).assertProblem(400, "INVALID_REQUEST");
    }

    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(true, false).flatMap(client -> Stream.of(null, "0", "-1", "invalid", "9223372036854775808")
            .map(value -> Arguments.of(client, value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "too-long", "case"})
    void test_orderNumberBindingAndExactMatching(String scenario) {
        orderData.mock(fixture);
        String orderNo = switch (scenario) {
            case "too-long" -> "x".repeat(65);
            case "case" -> fixture.getOrder().getOrderNo().replace("ord_", "ORD_");
            default -> scenario;
        };
        var result = invoke(Long.toString(checkoutDataMocker.clientId()), "1", orderNo, null, null);
        if ("case".equals(scenario)) {
            result.assertProblem(404, "ORDER_NOT_FOUND");
        } else {
            result.assertProblem(400, "INVALID_REQUEST");
        }
    }

    /** 不一致的持久化事实应作为系统错误暴露，不被包装成成功页或客户端参数错误。 */
    @ParameterizedTest
    @ValueSource(strings = {"zero-amount", "negative-amount", "precision", "zero-version", "version-overflow",
        "missing-client-reference", "operations-reference", "update-before-create", "nonterminal-completed",
        "terminal-incomplete", "completed-before-create", "completed-after-update", "missing-channel", "zero-channel"})
    void test_invalidStoredFacts(String scenario) {
        orderData.mock(fixture);
        var row = refund("broken").build();
        switch (scenario) {
            case "zero-amount" -> row.setRefundAmount(BigDecimal.ZERO);
            case "negative-amount" -> row.setRefundAmount(new BigDecimal("-1"));
            case "precision" -> row.setRefundAmount(new BigDecimal("0.001"));
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
        invoke(null, null).assertProblem(500, "INTERNAL_ERROR");
    }

    /** 多查的一行只决定是否有后页，其内容在真正返回该页时才校验。 */
    @Test
    void test_extraRowOnlyDeterminesNextCursor() {
        orderData.mock(fixture);
        refundData.mock(refund("valid").build());
        refundData.mock(refund("old").createTime(now().minusSeconds(1)).refundAmount(BigDecimal.ZERO).build());
        var first = invoke("1", null).assertPage(true, no("valid")).getResponseBody();
        invoke("1", first.getNextCursor()).assertProblem(500, "INTERNAL_ERROR");
    }

    /** API 没有请求 DTO；同一类直接复用 GET 适配方法，无需新增专用基类。 */
    private ListOrderRefundsResponseChecker invoke(String limit, String cursor) {
        return invoke(Long.toString(checkoutDataMocker.clientId()), "1", fixture.getOrder().getOrderNo(), limit,
            cursor);
    }

    private ListOrderRefundsResponseChecker invoke(String client, String credential, String orderNo, String limit,
        String cursor) {
        var before = queryChecker.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var uri = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/refunds");
        if (limit != null) {
            uri.queryParam("limit", limit);
        }
        if (cursor != null) {
            uri.queryParam("cursor", cursor);
        }
        var result = httpTestInvoker.get(uri.buildAndExpand(orderNo).toUriString(),
            TestRequestHeaders.client(REQUEST_ID, client, credential));
        queryChecker.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        return new ListOrderRefundsResponseChecker(result, objectMapper, REQUEST_ID);
    }

    private RefundRequest.RefundRequestBuilder refund(String suffix) {
        return refundData.template(fixture).refundRequestNo(no(suffix));
    }

    /** 合成编号包含本次隔离订单，固定前缀后的大小写用于验证真实排序。 */
    private String no(String suffix) {
        return "rfd_" + fixture.getOrder().getOrderId() + "_" + suffix;
    }

    private Instant now() {
        return adjustableUtcClock.instant();
    }

    /** 协议负例独立构造，不能调用被测 RefundCursor 编码器充当正确性依据。 */
    private static String cursor(Instant time, String number) {
        return encoded(time + "\n" + number);
    }

    private static String encoded(String payload) {
        return "r1."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.US_ASCII));
    }
}
