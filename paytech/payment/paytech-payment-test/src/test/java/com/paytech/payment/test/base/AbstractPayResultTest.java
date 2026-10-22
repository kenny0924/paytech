package com.paytech.payment.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.service.handler.PayResultQueryHandler;

/**
 * 3DS 全链路“确认支付结果”入口测试的公共准备：经真实 pay 入口形成处理中的 PAY，并等待 PAY 结果事务登记的查单恢复任务完成首轮执行
 * （首轮查单按渠道 Mock 的“仍处理中 / 结果未知”配置不推进动作），避免与本例的刷新、通知或显式扫描并发。不放 {@code @Test}。
 */
public abstract class AbstractPayResultTest extends AbstractPayCardTest {
    /** 合成 3DS 跳转地址。 */
    protected static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    /** 待认证的支付：PAY 处理中、记 3DS 交互；查单恢复任务首轮已执行完毕。 */
    protected PendingPay pendingPay(String channelTransactionRef) {
        channel.pending(channelTransactionRef, REDIRECT_URL);
        return submitAndAwait(newPayment());
    }

    /** 结果未知的支付：PAY 为 UNKNOWN；查单恢复任务首轮已执行完毕。 */
    protected PendingPay unknownPay() {
        channel.unknown();
        return submitAndAwait(newPayment());
    }

    /** 提交 pay 并等待查单恢复任务首轮结束（任务进入 ERROR 等待退避）。 */
    PendingPay submitAndAwait(Payment payment) {
        var response = payment.pay();
        assertEquals(202, response.getStatusCode().value());
        long attemptId = attemptId(response);
        awaitQueryRetrying(attemptId);
        var pay = mapper.actions(attemptId).get(1);
        assertEquals(ActionType.PAY.getCode(), pay.get("type"));
        return new PendingPay(payment, attemptId, ((Number)pay.get("actionId")).longValue());
    }

    /** 等待该尝试的查单恢复任务处于 ERROR（已执行、等待退避重试）。 */
    protected void awaitQueryRetrying(long attemptId) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> {
            Map<String, Object> event = mapper.compensationEvent(PayResultQueryHandler.EVENT_TYPE, String.valueOf(attemptId));
            return event != null && "ERROR".equals(event.get("status"));
        });
    }

    /** 等待该尝试的查单恢复任务被删除（确认得到终态）。 */
    protected void awaitQueryDone(long attemptId) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
            .until(() -> mapper.compensationEvent(PayResultQueryHandler.EVENT_TYPE, String.valueOf(attemptId)) == null);
    }

    /** 经真实 HTTP 入口刷新来源提交的支付结果；4xx/5xx 使测试失败。 */
    protected ResponseEntity<Map<String, Object>> refresh(Payment payment) {
        return refresh(payment, payment.clientId);
    }

    /** 以指定 Client 刷新来源提交的支付结果；4xx/5xx 使测试失败。 */
    protected ResponseEntity<Map<String, Object>> refresh(Payment payment, String clientId) {
        return post("/payment-execution/v1/intents/" + payment.intentId + "/submissions/" + payment.submissionId + "/refresh",
            Map.of("clientId", clientId), Map.of());
    }

    /** PAY 动作的当前状态。 */
    protected String payStatus(PendingPay pending) {
        return (String)mapper.actions(pending.attemptId).get(1).get("status");
    }

    /** 当前状态为指定值。 */
    protected static boolean is(String actual, ActionStatus expected) {
        return expected.getCode().equals(actual);
    }

    /**
     * 一笔停在 PAY 的支付
     *
     * @param payment 支付身份
     * @param attemptId 支付尝试 ID
     * @param payActionId PAY 动作 ID
     */
    protected record PendingPay(Payment payment, long attemptId, long payActionId) {
    }
}
