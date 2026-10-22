package com.paytech.payment.auto.recovery;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.service.handler.PayResultQueryHandler;
import com.paytech.payment.test.base.AbstractPayResultTest;

/**
 * PAY 查单恢复任务入口（3DS 全链路 technique 3.2，D3）：任务由 PAY 结果事务登记，提交后立即执行首轮；之后经生产 Scanner 显式扫描到期
 * 任务，由真实 Dispatcher 执行 Handler。
 *
 * <p>
 * 覆盖：首轮仍处理中按退避等待（AC-04 的“退避周期”）；渠道确认成功后推进并删除任务、PaymentSucceeded 只发布一次；动作创建已超过 24 小时
 * 仍无结论转 FAILED（AC-09、Q4）；渠道成功但金额不一致转 FAILED；事件身份非法为终态失败。
 *
 * @see PayResultQueryHandler#handle(CompensationEvent)
 */
class PayResultQueryHandlerTest extends AbstractPayResultTest {
    private static final CompensationScanRequest SCAN =
        new CompensationScanRequest(100, 0, 1, Set.of(PayResultQueryHandler.EVENT_TYPE));

    @Autowired
    private CompensationEventScanner scanner;

    @Autowired
    private PayResultQueryHandler handler;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 首轮查单仍处理中：任务 ERROR、执行 1 次、下次执行时间在未来；渠道成功后到期扫描推进并删除任务，PaymentSucceeded 只发布一次。 */
    @Test
    void test_backoffThenSucceeded() {
        PendingPay pending = pendingPay("T-RQ-1");
        Map<String, Object> first = event(pending);
        assertAll(() -> assertEquals(1, ((Number)first.get("executeCount")).intValue()),
            () -> assertEquals(1L, ((Number)first.get("deferred")).longValue(), "首轮后按退避等待"),
            () -> assertEquals("PAY_RESULT_PENDING", first.get("errorCode")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
        channel.querySucceeded("T-RQ-1", AMOUNT, CURRENCY);

        scanDue(pending);
        awaitQueryDone(pending.attemptId());

        assertAll(() -> assertEquals(ActionStatus.SUCCEEDED.getCode(), payStatus(pending)),
            () -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), mapper.attempt(pending.attemptId()).get("status")),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), mapper.intent(pending.payment().intentId).get("status")),
            () -> assertEquals(1, mapper.transactionRefs().size()));
        results.awaitEnvelope(pending.attemptId());
    }

    /** 动作创建已超过 24 小时仍处理中：任务转 FAILED（PAY_RESULT_UNRESOLVED），不自动判支付失败，Attempt 保持处理中（转人工）。 */
    @Test
    void test_unresolvedAfter24Hours() {
        PendingPay pending = pendingPay("T-RQ-24");
        mapper.ageAction(pending.payActionId(), 25 * 3600);

        scanDue(pending);
        awaitStatus(pending, "FAILED");

        assertAll(() -> assertEquals("PAY_RESULT_UNRESOLVED", event(pending).get("errorCode")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)),
            () -> assertEquals(AttemptStatus.PROCESSING.getCode(), mapper.attempt(pending.attemptId()).get("status")));
        results.assertNoSucceeded(pending.attemptId());
    }

    /** 渠道报告成功但金额不一致：不推进动作，任务转 FAILED（PAY_RESULT_AMOUNT_MISMATCH）转人工。 */
    @Test
    void test_amountMismatchFailed() {
        PendingPay pending = pendingPay("T-RQ-M");
        channel.querySucceeded("T-RQ-M", AMOUNT, "USD");

        scanDue(pending);
        awaitStatus(pending, "FAILED");

        assertAll(() -> assertEquals("PAY_RESULT_AMOUNT_MISMATCH", event(pending).get("errorCode")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
        results.assertNoSucceeded(pending.attemptId());
    }

    /** 事件键非法、带载荷或尝试不存在：终态失败，不查单。 */
    @Test
    void test_invalidIdentityTerminal() {
        for (var event : new CompensationEvent[] {
            new CompensationEvent(1, "abc", PayResultQueryHandler.EVENT_TYPE, "-", null, 1),
            new CompensationEvent(1, "12", PayResultQueryHandler.EVENT_TYPE, "-", "{}", 1),
            new CompensationEvent(1, "999999999999", PayResultQueryHandler.EVENT_TYPE, "-", null, 1)}) {
            assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, handler.handle(event).outcome());
        }
        assertTrue(channel.queryCalls().isEmpty());
    }

    /** 把该尝试的任务拨到到期并经生产 Scanner 扫描一次。 */
    void scanDue(PendingPay pending) {
        assertEquals(1, mapper.dueNow(PayResultQueryHandler.EVENT_TYPE, String.valueOf(pending.attemptId())));
        scanner.scan(SCAN);
    }

    /** 等待该尝试的任务进入指定执行状态。 */
    void awaitStatus(PendingPay pending, String status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
            .until(() -> status.equals(event(pending).get("status")));
    }

    /** 该尝试的查单恢复任务。 */
    Map<String, Object> event(PendingPay pending) {
        return mapper.compensationEvent(PayResultQueryHandler.EVENT_TYPE, String.valueOf(pending.attemptId()));
    }
}
