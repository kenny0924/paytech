package com.paytech.payment.execution.service.handler;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.service.PaymentExecutionService;
import com.paytech.payment.execution.service.processor.ConfirmPayResultProcessor;
import com.paytech.payment.execution.service.processor.request.ConfirmPayResultRequest;
import com.paytech.payment.execution.service.processor.response.ConfirmPayResultResponse;

import lombok.AllArgsConstructor;

/**
 * PAY 查单恢复（3DS 全链路 technique 3.2，D3）：PAY 结果事务保存为 PROCESSING 或 UNKNOWN 时同事务登记，提交后立即执行一次，之后按退避
 * 重试，直到“确认支付结果”得到终态。
 * <li>eventKey 是支付尝试 ID；Handler 经 PaymentExecutionService 取尝试所属 Intent，调用 {@link ConfirmPayResultProcessor}，补偿记录不保存
 * 业务内容。</li>
 * <li>终态或无需确认 → 成功（删除任务）；渠道仍在处理、查单没有结论或锁冲突 → 可重试；动作创建已超过 24 小时仍无结论 →
 * {@value #UNRESOLVED} 终态失败、任务置 FAILED 告警转人工（Q4，不自动判失败）；渠道报告成功但金额不一致 → {@value #AMOUNT_MISMATCH}
 * 终态失败转人工。</li>
 * <li>策略：每次 20 秒，最多 56 次，10 秒起按 2 倍退避至 30 分钟、抖动 0.2，覆盖约 24 小时；重试只靠 XXL 30 秒扫描唤醒，实际间隔 ≥ 30 秒。</li>
 */
@Component
@AllArgsConstructor
public class PayResultQueryHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "PAY_RESULT_QUERY";

    /** 超过人工处理期限仍无结论的终态失败代码。 */
    static final String UNRESOLVED = "PAY_RESULT_UNRESOLVED";

    /** 渠道成功金额不一致的终态失败代码。 */
    static final String AMOUNT_MISMATCH = "PAY_RESULT_AMOUNT_MISMATCH";

    /** 查单恢复的最长期限，超过后转人工。 */
    static final Duration RESOLUTION_WINDOW = Duration.ofHours(24);

    private final Clock clock;
    private final PaymentExecutionService paymentExecutionService;
    /**
     * 确认支付结果入口
     * <li>延迟取得：确认流程依赖动作流程，动作流程中的 PAY 依赖补偿登记器，登记器在创建时收集全部 Handler（含本类），直接注入会形成循环。</li>
     */
    private final ObjectProvider<ConfirmPayResultProcessor> confirmPayResultProcessor;

    /** 纯工厂：与 PAY 保存为 PROCESSING / UNKNOWN 在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long attemptId) {
        if (attemptId <= 0) {
            throw new IllegalArgumentException("Invalid payment attempt identity");
        }
        return new CompensationEventRequest(Long.toString(attemptId), EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒，最多 56 次，10 秒起指数退避至 30 分钟，抖动 0.2。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 56, Duration.ofSeconds(10), Duration.ofMinutes(30), 2,
            0.2);
    }

    /**
     * 确认一次支付结果
     * <li>尝试不存在或身份格式非法为终态失败；Intent 锁冲突与依赖不可用为可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("PAY_RESULT_QUERY_TRANSACTION_ACTIVE",
                "Payment result query must run outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("PAY_RESULT_QUERY_INVALID", "Payment result query is malformed");
        }
        PaymentAttempt attempt = paymentExecutionService.findAttempt(Long.parseLong(event.eventKey()));
        if (attempt == null) {
            return CompensationHandlerResult.terminalFailure("PAY_RESULT_QUERY_INVALID", "Payment attempt does not exist");
        }
        ConfirmPayResultResponse response;
        try {
            response = confirmPayResultProcessor.getObject()
                .process(new ConfirmPayResultRequest(attempt.getPaymentIntentId(), attempt.getPaymentAttemptId()));
        } catch (ConcurrentRequestException | ServiceUnavailableException busy) {
            return CompensationHandlerResult.retryableFailure("PAY_RESULT_QUERY_BUSY", "Payment intent is busy");
        } catch (FailException rejected) {
            return CompensationHandlerResult.terminalFailure("PAY_RESULT_QUERY_INVALID", "Payment attempt cannot be confirmed");
        }
        return result(response);
    }

    /** 按确认结论决定删除、重试或转人工。 */
    CompensationHandlerResult result(ConfirmPayResultResponse response) {
        return switch (response.getConfirmation()) {
            case CONFIRMED -> CompensationHandlerResult.success();
            case AMOUNT_MISMATCH -> CompensationHandlerResult.terminalFailure(AMOUNT_MISMATCH,
                "Channel reported a different amount; manual handling required");
            case PENDING -> unresolved(response) ? CompensationHandlerResult.terminalFailure(UNRESOLVED,
                "Payment result unresolved after 24 hours; manual handling required")
                : CompensationHandlerResult.retryableFailure("PAY_RESULT_PENDING", "Payment result not yet final");
        };
    }

    /** 动作创建已超过 24 小时仍无结论。 */
    boolean unresolved(ConfirmPayResultResponse response) {
        return response.getActionCreateTime() != null
            && response.getActionCreateTime().plus(RESOLUTION_WINDOW).isBefore(clock.instant());
    }
}
