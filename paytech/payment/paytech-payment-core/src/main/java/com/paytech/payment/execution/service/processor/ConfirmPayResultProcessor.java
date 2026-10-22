package com.paytech.payment.execution.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payment.execution.core.action.ActionResult;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.action.PayActionActor;
import com.paytech.payment.execution.core.action.flow.ActionFlowFactory;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.ChannelExecutionOutcome;
import com.paytech.payment.execution.core.enums.PayResultConfirmation;
import com.paytech.payment.execution.domain.ChannelExecutionCommand;
import com.paytech.payment.execution.domain.ChannelExecutionResult;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domain.PayInput;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.execution.service.processor.request.ConfirmPayResultRequest;
import com.paytech.payment.execution.service.processor.response.ConfirmPayResultResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 确认支付结果（3DS 全链路 technique 3.2）：查单恢复任务、收银台刷新与渠道通知三处入口共用，只采信渠道查单结果。
 *
 * <p>
 * 与 pay、终止共用 Intent 资源锁（TRY_LOCK 等待 500 毫秒、自动续租），拿不到锁时抛出并发冲突，由入口按各自合同处理。锁内先读已提交事实：
 * 只有当前（序号最大的）动作是 PROCESSING / UNKNOWN 的 PAY 才查单，其他情况直接返回当前事实。事务外经 channel-connector 以冻结坐标执行
 * QUERY（ORIGINAL_TRANSACTION），按下表映射后经 {@code ActionFlow.complete} 的短事务推进；版本与原状态 CAS 保证终态只写一次。
 * <li>SUCCESS + SUCCEEDED 且金额、币种与动作一致 → 成功；金额或币种不一致 → 不推进，记录错误日志（只含 ID）并返回转人工结论。</li>
 * <li>SUCCESS + FAILED / CLOSED → 失败（失败代码取渠道代码；CLOSED 为 {@value #CHANNEL_ORDER_CLOSED}）。</li>
 * <li>FAIL + NOT_FOUND 且动作创建已超过 10 分钟 → 失败（{@value #CHANNEL_TRANSACTION_NOT_FOUND}）；10 分钟内视为渠道尚未落单。</li>
 * <li>SUCCESS + PENDING：原为 UNKNOWN 时改回 PROCESSING，原为 PROCESSING 时不写库；其他结论、NOT_SENT、ERROR 与 HTTP 失败不推进。</li>
 * <li>推进结果带上原动作的交互类型与期限，满足“完成后保留历史值”；不重发扣款，跳转地址不经过本流程。</li>
 */
@Slf4j
@Component
@AllArgsConstructor
public class ConfirmPayResultProcessor extends AbstractLockProcessor<ConfirmPayResultRequest, ConfirmPayResultResponse> {
    /** 渠道订单已关闭未支付时的失败代码。 */
    public static final String CHANNEL_ORDER_CLOSED = "CHANNEL_ORDER_CLOSED";

    /** 渠道无此交易（且动作已超过落单宽限）时的失败代码。 */
    public static final String CHANNEL_TRANSACTION_NOT_FOUND = "CHANNEL_TRANSACTION_NOT_FOUND";

    /** 渠道无此交易时视为尚未落单的宽限期。 */
    static final Duration NOT_FOUND_GRACE = Duration.ofMinutes(10);

    /** 等待 Intent 资源锁的上限（毫秒）。 */
    static final int LOCK_WAIT_MILLIS = 500;

    final Clock clock;
    final ActionFlowFactory actionFlowFactory;
    final PaymentChannelClient paymentChannelClient;
    final PaymentActionRepository paymentActionRepository;
    final PaymentIntentRepository paymentIntentRepository;
    final PaymentAttemptRepository paymentAttemptRepository;
    final PaymentInstrumentTokenRepository paymentInstrumentTokenRepository;

    /** 在 500 毫秒内尝试获取与 pay、终止相同的 Intent 资源锁。 */
    @Override
    protected LockCommand lockCommand(ConfirmPayResultRequest request) {
        return LockCommand.tryLock(Locks.getIntentLock(request.getIntentId()), LOCK_WAIT_MILLIS, -1);
    }

    @Override
    protected ConfirmPayResultResponse processWithinLock(ConfirmPayResultRequest request) {
        // 读取已提交事实
        PaymentAttempt attempt = getAndCheckAttempt(request);
        PaymentAction action = paymentActionRepository.findLatestByAttempt(attempt.getPaymentAttemptId());
        if (action == null || action.getActionType() != ActionType.PAY
            || !action.statusIn(ActionStatus.PROCESSING, ActionStatus.UNKNOWN)) {
            return response(PayResultConfirmation.CONFIRMED, attempt, action);
        }
        PayContext context = buildContext(attempt);

        // 事务外查单
        ChannelExecutionResult queried = query(context);

        // 结果映射
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (amountMismatch(action, queried)) {
            log.error("渠道报告成功但金额或币种与支付动作不一致，转人工: attemptId={}, actionId={}",
                attempt.getPaymentAttemptId(), action.getPaymentActionId());
            return response(PayResultConfirmation.AMOUNT_MISMATCH, attempt, action);
        }
        ActionResult result = interpret(action, queried, now);
        if (result == null) {
            return response(PayResultConfirmation.PENDING, attempt, action);
        }

        // 短事务推进
        actionFlowFactory.getFlow(context.actionFlowKey()).complete(context, action, result);
        return response(action.isFinal() ? PayResultConfirmation.CONFIRMED : PayResultConfirmation.PENDING, attempt,
            action);
    }

    /** 支付尝试必须存在且属于请求的支付意图。 */
    PaymentAttempt getAndCheckAttempt(ConfirmPayResultRequest request) {
        PaymentAttempt attempt = paymentAttemptRepository.selectById(request.getAttemptId());
        if (attempt == null || !Objects.equals(attempt.getPaymentIntentId(), request.getIntentId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付尝试不存在");
        }
        return attempt;
    }

    /** 由已提交的意图、尝试与工具组成上下文；没有本次提交的渠道输入，不会重发扣款。 */
    PayContext buildContext(PaymentAttempt attempt) {
        PaymentIntent intent = paymentIntentRepository.findIntent(attempt.getPaymentIntentId());
        return new PayContext(intent, attempt, paymentInstrumentTokenRepository.findToken(attempt.getPaymentInstrumentTokenId()));
    }

    /** 以冻结坐标执行原交易查单；HTTP 失败、超时等一律按没有结论处理。 */
    ChannelExecutionResult query(PayContext context) {
        var attempt = context.getAttempt();
        var command = new ChannelExecutionCommand();
        command.setOperation(ChannelOperation.QUERY);
        command.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION);
        command.setEntityNo(context.getIntent().getEntityNo());
        command.setChannelId(attempt.getChannelId());
        command.setChannelAccountId(attempt.getChannelAccountId());
        command.setMidId(attempt.getMidId());
        var payment = new LinkedHashMap<String, Object>();
        payment.put(PayInput.ATTEMPT_ID, attempt.getPaymentAttemptId().toString());
        command.setInput(Map.of(PayInput.PAYMENT, payment));
        try {
            return paymentChannelClient.execute(command);
        } catch (RuntimeException failure) {
            log.warn("查单未取得结论: attemptId={}, failure={}", attempt.getPaymentAttemptId(),
                failure.getClass().getSimpleName());
            return ChannelExecutionResult.unknown();
        }
    }

    /** 渠道报告成功，但金额或币种缺失或与动作不一致。 */
    static boolean amountMismatch(PaymentAction action, ChannelExecutionResult queried) {
        if (queried.getOutcome() != ChannelExecutionOutcome.SUCCESS
            || queried.getPaymentStatus() != ChannelPaymentStatus.SUCCEEDED) {
            return false;
        }
        return queried.getAmount() == null || queried.getAmount().compareTo(action.getAmount()) != 0
            || !action.getCurrency().equals(queried.getCurrency());
    }

    /**
     * 查单结论映射为动作结果
     *
     * @return 需要写入的结果；不推进（含 PROCESSING 仍在处理）时为 null
     */
    static ActionResult interpret(PaymentAction action, ChannelExecutionResult queried, Instant now) {
        if (queried.getOutcome() == ChannelExecutionOutcome.FAIL
            && queried.getPaymentStatus() == ChannelPaymentStatus.NOT_FOUND) {
            boolean graceOver = action.getCreateTime().plus(NOT_FOUND_GRACE).isBefore(now);
            return graceOver ? carry(action, ActionStatus.FAILED, CHANNEL_TRANSACTION_NOT_FOUND, null, null, now) : null;
        }
        if (queried.getOutcome() != ChannelExecutionOutcome.SUCCESS || queried.getPaymentStatus() == null) {
            return null;
        }
        return switch (queried.getPaymentStatus()) {
            case SUCCEEDED -> carry(action, ActionStatus.SUCCEEDED, queried.getChannelStatus(),
                queried.getFailureMessage(), queried.getChannelTransactionRef(), now);
            case FAILED -> carry(action, ActionStatus.FAILED, queried.getFailureCode(), queried.getFailureMessage(),
                queried.getChannelTransactionRef(), now);
            case CLOSED -> carry(action, ActionStatus.FAILED, CHANNEL_ORDER_CLOSED, queried.getFailureMessage(),
                queried.getChannelTransactionRef(), now);
            case PENDING -> action.statusIn(ActionStatus.UNKNOWN) ? carry(action, ActionStatus.PROCESSING,
                queried.getChannelStatus(), queried.getFailureMessage(), queried.getChannelTransactionRef(), now) : null;
            case NOT_FOUND -> null;
        };
    }

    /**
     * 完整结果：保留原动作的风控与交互字段，只替换状态与渠道结论
     * <li>渠道说明截断到列长；不合规的响应代码不保存。</li>
     */
    static ActionResult carry(PaymentAction action, ActionStatus status, String code, String message,
        String channelTransactionId, Instant now) {
        var result = new ActionResult();
        result.setStatus(status);
        result.setTransactionAssessmentId(action.getTransactionAssessmentId());
        result.setRiskResult(action.getRiskResult());
        result.setAuthenticationRequirement(action.getAuthenticationRequirement());
        result.setInteractionType(action.getInteractionType());
        result.setInteractionExpiresAt(action.getInteractionExpiresAt());
        result.setChannelResponseCode(PayActionActor.safeCode(code));
        result.setChannelResponseMsg(message == null ? null : message.substring(0, Math.min(message.length(), 512)));
        result.setChannelResultTime(now);
        result.setChannelTransactionId(channelTransactionId);
        return result;
    }

    /** 结论、尝试当前状态与动作创建时间。 */
    static ConfirmPayResultResponse response(PayResultConfirmation confirmation, PaymentAttempt attempt,
        PaymentAction action) {
        return new ConfirmPayResultResponse(confirmation, attempt.getAttemptStatus(),
            action == null ? null : action.getCreateTime());
    }
}
