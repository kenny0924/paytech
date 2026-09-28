package com.paytech.commerce.test.checker.refund;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.commerce.client.api.v1.refund.request.CreateRefundRequestBody;
import com.paytech.commerce.client.api.v1.refund.response.CreateRefundResponseBody;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domain.enums.RefundRequestOrigin;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.dal.OrderQueryTestMapper;
import com.paytech.commerce.test.dal.RefundCreationTestMapper;
import com.paytech.commerce.test.dal.RefundCreationTestMapper.Task;

import lombok.AllArgsConstructor;

/** 从真实数据库验证受理、预占、补偿任务以及拒绝后的零写入。 */
@TestComponent
@AllArgsConstructor
public class RefundCreationChecker {
    private final RefundCreationTestMapper mapper;
    private final OrderQueryTestMapper orders;

    /** Payment 尚未接入时应等待重试，不能回滚已受理事实或伪造退款成功。 */
    public void assertAccepted(Fixture fixture, CreateRefundRequestBody request, CreateRefundResponseBody response) {
        var task = awaitRetry(response.getRefundRequestNo());
        var rows = mapper.refunds(fixture.getOrder().getOrderId());
        assertEquals(1, rows.size());
        var refund = rows.getFirst();
        assertAll(() -> assertEquals(response.getRefundRequestNo(), refund.getRefundRequestNo()),
            () -> assertTrue(refund.getRefundRequestId() > 0),
            () -> assertEquals(fixture.getOrder().getOrderId(), refund.getOrderId()),
            () -> assertTrue(refund.getRefundRequestNo().matches("rfd_[0-9A-Za-z]{27,32}")),
            () -> assertEquals(fixture.getOrder().getClientId(), refund.getClientId()),
            () -> assertEquals(RefundRequestOrigin.CLIENT, refund.getRequestOrigin()),
            () -> assertEquals(request.getClientRefundId(), refund.getClientRefundId()),
            () -> assertMoney(request.getAmount(), refund.getRefundAmount()),
            () -> assertEquals(fixture.getOrder().getCurrency(), refund.getCurrency()),
            () -> assertEquals(request.getReasonCode(), refund.getReasonCode()),
            () -> assertEquals(request.getReasonSummary(), refund.getReasonSummary()),
            () -> assertEquals(RefundStatus.PROCESSING, refund.getRefundStatus()),
            () -> assertNull(refund.getChannelRefundId()), () -> assertNull(refund.getCompletedAt()),
            () -> assertNull(refund.getStatusReasonCode()), () -> assertEquals(2, refund.getVersion()),
            () -> assertEquals(response.getCreatedAt(), refund.getCreateTime()),
            () -> assertEquals(response.getCreatedAt(), refund.getUpdateTime()));
        assertEquals(1, mapper.tasks(fixture.getOrder().getOrderId()).size());
        assertTask(task, response.getRefundRequestNo());
    }

    /** 等待明确持久化结果，不使用固定睡眠或手动唤醒来掩盖 afterCommit 缺失。 */
    public Task awaitRetry(String refundNo) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            var task = mapper.task(refundNo);
            assertNotNull(task, "退款事务必须登记提交任务");
            assertEquals("ERROR", task.getExecuteStatus(), "真实 Handler 应提交失败并保留恢复记录");
        });
        return mapper.task(refundNo);
    }

    public void assertTask(Task task, String refundNo) {
        assertAll(() -> assertEquals(refundNo, task.getEventKey()),
            () -> assertEquals("SUBMISSION_EVENT_TYPE", task.getEventType()),
            () -> assertEquals("-", task.getSubEventType()), () -> assertNull(task.getPayloadJson()),
            () -> assertEquals(1, task.getExecuteCount()), () -> assertNull(task.getLockOwner()),
            () -> assertNull(task.getLockUntil()), () -> assertEquals("INTERNAL_ERROR", task.getLastErrorCode()),
            () -> assertEquals("Handler execution failed unexpectedly.", task.getLastErrorSummary()),
            () -> assertTrue(task.getNextExecuteAt().isAfter(task.getUpdateTime())));
    }

    public RefundRequest onlyRefund(long orderId) {
        var rows = mapper.refunds(orderId);
        assertEquals(1, rows.size());
        return rows.getFirst();
    }

    /** 检查新预占和既有成功退款分别入账，只有金额位置版本递增。 */
    public void assertPosition(long orderId, String reserved, String refunded, long version) {
        var position = mapper.position(orderId);
        assertNotNull(position);
        assertAll(() -> assertMoney("50", position.getRefundableBaseAmount()),
            () -> assertMoney(reserved, position.getReservedAmount()),
            () -> assertMoney(refunded, position.getRefundedAmount()),
            () -> assertEquals(version, position.getVersion()));
    }

    /** 捕获全列快照只用于零副作用比较，不据此生成业务响应期望。 */
    public List<List<Map<String, Object>>> snapshot(long orderId) {
        return List.of(orders.orderRows(orderId), orders.paymentRows(orderId), orders.sessionRows(orderId),
            orders.refundRows(orderId), orders.fundsRows(orderId));
    }

    /** 退款入口不能修改订单、原支付、Checkout、商品或商业上下文。 */
    public List<List<Map<String, Object>>> unchangedFacts(long orderId) {
        return List.of(orders.orderRows(orderId), orders.paymentRows(orderId), orders.sessionRows(orderId),
            orders.fundsRows(orderId), orders.itemRows(orderId), orders.contextRows(orderId));
    }

    public void assertRejected(long orderId, List<List<Map<String, Object>>> before) {
        assertEquals(before, snapshot(orderId));
        assertTrue(mapper.refunds(orderId).isEmpty(), "拒绝后不能留下退款申请");
        assertTrue(mapper.tasks(orderId).isEmpty(), "拒绝后不能登记提交任务");
    }

    /** 事务回滚后按任务身份独立查询，防止关联行删除使 JOIN 掩盖孤立任务。 */
    public void assertNoTask(String refundNo) {
        assertNull(mapper.task(refundNo));
    }

    public static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
