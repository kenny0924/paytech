package com.paytech.payment.execution.core.action;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.domain.*;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentCardClient;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 直接收款（PayerMax 卡直付对接技术方案第 5 节）：经 channel-connector 端口以冻结坐标执行渠道操作 PAY，卡只以 card 签发的一次性读卡票据传递。
 *
 * <p>
 * 读卡票据（design/05 §2.1）：发起渠道调用前（事务外、Intent 锁内）先完成准入校验（Attempt 处理中、当前动作为执行中的 PAY 且就是本动作、
 * 支付工具未撤销），再向 card 申请本次动作的票据，交给 Platform 标准 API 作为卡字段句柄。准入不成立、card 拒绝签发（如 CVV 已领取）或 card
 * 不可用时，渠道请求未发出，动作以 NOT_SENT 语义明确失败（{@value #CARD_MATERIAL_UNAVAILABLE}）。
 *
 * <p>
 * 只消费 channel-connector 已映射的结论（D06），不解释 Platform 输出键：SUCCESS + SUCCEEDED → 成功；SUCCESS + PENDING → 处理中（有跳转时记
 * 3DS 交互，指令只放入本次 Context）；SUCCESS + FAILED、FAIL、NOT_SENT → 失败；SUCCESS + 无法识别的状态、ERROR → 未知。恢复（渠道通知与查询）
 * 本期不做，PROCESSING/UNKNOWN 保持原状；不重发扣款。
 */
@Slf4j
@Component
public class PayActionActor extends AbstractActionActor<PayContext> {
    /** 3DS 等交互的有效期上限；渠道订单最短有效期 1800 秒。 */
    static final Duration INTERACTION_WINDOW = Duration.ofMinutes(30);

    /** 未取得读卡票据、渠道请求未发出时的失败代码。 */
    public static final String CARD_MATERIAL_UNAVAILABLE = "CARD_MATERIAL_UNAVAILABLE";

    private final Clock clock;
    private final PaymentChannelClient paymentChannelClient;
    private final PaymentCardClient paymentCardClient;
    private final CompensationEventRegistrar events;

    /** 注入 Channel 与 card 端口、收款结果持久化与补偿任务登记。 */
    public PayActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts, Clock clock,
        PaymentChannelClient paymentChannelClient, PaymentCardClient paymentCardClient,
        CompensationEventRegistrar events) {
        super(intents, attempts);
        this.clock = clock;
        this.paymentChannelClient = paymentChannelClient;
        this.paymentCardClient = paymentCardClient;
        this.events = events;
    }

    /** 返回动作代码。 */
    @Override
    public String getCode() {
        return ActionType.PAY.getCode();
    }

    /** 在事务外执行原动作；交互指令写入本次 Context，不随结果保存。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        if (context.getChannelInput() == null) {
            // 只有本次新提交携带渠道交互输入；其他入口不能重发扣款。
            return ActionResult.unknown();
        }
        String cardHandle = cardHandle(context, action);
        if (cardHandle == null) {
            return failed(null, null, clock.instant().truncatedTo(ChronoUnit.MICROS), CARD_MATERIAL_UNAVAILABLE);
        }
        ChannelExecutionResult channel = paymentChannelClient.execute(command(context, cardHandle));
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return switch (channel.getOutcome()) {
            case SUCCESS -> accepted(context, channel, now);
            case FAIL -> rejected(channel, now);
            case NOT_SENT -> failed(null, null, now, channel.getFailureCode());
            case ERROR -> ActionResult.unknown();
        };
    }

    /**
     * 卡字段句柄：准入校验通过后向 card 申请本次动作的一次性读卡票据
     *
     * @return 票据；准入不成立、card 拒绝或不可用时为 null（渠道请求不发出）
     */
    String cardHandle(PayContext context, PaymentAction action) {
        var token = context.getInstToken();
        if (token.getInstrumentType() != InstrumentType.CARD) {
            return token.getPaymentInstrumentToken();
        }
        var attempt = context.getAttempt();
        if (attempt.getAttemptStatus() != AttemptStatus.PROCESSING
            || !Objects.equals(attempt.getActiveActionId(), action.getPaymentActionId())
            || !action.statusIn(ActionStatus.PROCESSING) || action.getActionType() != ActionType.PAY
            || token.getRevokedAt() != null) {
            log.warn("卡支付准入不成立，不申请读卡票据: attemptId={}, actionId={}", attempt.getPaymentAttemptId(),
                action.getPaymentActionId());
            return null;
        }
        try {
            return paymentCardClient.issueMaterialTicket(token.getInstrumentRef(), attempt.getPaymentAttemptId(),
                action.getPaymentActionId());
        } catch (RuntimeException rejected) {
            log.warn("card 未签发读卡票据，渠道请求不发出: attemptId={}, actionId={}, failure={}",
                attempt.getPaymentAttemptId(), action.getPaymentActionId(), rejected.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 收款成功后完成 Attempt 和 Intent；只写 Payment 自己的表（design/05 §2.3：卡凭证验证与存卡改由 card 消费 PaymentSucceeded）。
     * <li>同事务登记 PaymentSucceeded 发布任务，提交后由补偿 Handler 发布；事务回滚时任务一并回滚。</li>
     */
    @Override
    public void doInTransaction(ActionContext context, PaymentAction action) {
        assert action.statusIn(ActionStatus.SUCCEEDED);
        var attempt = context.getAttempt();
        attempts.payCompleted(attempt, action.getAmount());
        intents.attemptSucceed(context.getIntent(), attempt.getPaymentAttemptId(), IntentStatus.SUCCEEDED);
        events.register(PaymentSucceededHandler.buildEvent(attempt.getPaymentAttemptId()));
    }

    /** 以冻结坐标与新支付维度组装渠道动作；输入不含卡值，卡只以读卡票据出现。 */
    static ChannelExecutionCommand command(PayContext context, String cardHandle) {
        var intent = context.getIntent();
        var attempt = context.getAttempt();
        var command = new ChannelExecutionCommand();
        command.setOperation(ActionChannelOperations.of(ActionType.PAY));
        command.setEntityNo(intent.getEntityNo());
        command.setChannelId(attempt.getChannelId());
        command.setChannelAccountId(attempt.getChannelAccountId());
        command.setMidId(attempt.getMidId());
        command.setPaymentMethodCode(attempt.getPaymentMethodCode());
        command.setCountryCode(intent.getConsumerCountryCode());
        command.setCurrency(intent.getCurrency());
        command.setMccCode(intent.getMccCode());
        command.setAmount(intent.getAmount());
        command.setInput(input(context, cardHandle).toMap());
        return command;
    }

    /** 标准 API PAY 输入：卡字段句柄（读卡票据）、本次尝试与金额、回跳地址、付款人引用及可选网络信息。 */
    static PayInput input(PayContext context, String cardHandle) {
        var intent = context.getIntent();
        var channelInput = context.getChannelInput();
        var input = new PayInput();
        input.setCardToken(cardHandle);
        input.setAttemptId(context.getAttempt().getPaymentAttemptId().toString());
        input.setAmount(amountText(intent.getAmount(), intent.getCurrency()));
        input.setCurrency(intent.getCurrency());
        input.setCountry(intent.getConsumerCountryCode());
        input.setReturnUrl(channelInput.getReturnUrl());
        input.setPayerReference(channelInput.getConsumerReference());
        input.setClientIp(channelInput.getConsumerIpAddress());
        input.setUserAgent(channelInput.getConsumerUserAgent());
        return input;
    }

    /** 金额按币种精度写成主单位十进制字符串（如 AED 10.00）；超出币种精度时拒绝，不舍入。 */
    public static String amountText(BigDecimal amount, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        return amount.setScale(Math.max(digits, 0), RoundingMode.UNNECESSARY).toPlainString();
    }

    /** 渠道已受理：按 channel-connector 映射的支付状态处理；无法识别的状态视为未知。 */
    ActionResult accepted(PayContext context, ChannelExecutionResult channel, Instant now) {
        ChannelPaymentStatus status = channel.getPaymentStatus();
        ActionResult result = status == null ? ActionResult.unknown() : switch (status) {
            case SUCCEEDED -> result(ActionStatus.SUCCEEDED, channel.getChannelStatus(), channel.getFailureMessage(), now);
            case PENDING -> pending(context, channel, now);
            case FAILED -> rejected(channel, now);
        };
        result.setChannelTransactionId(channel.getChannelTransactionRef());
        return result;
    }

    /** 待认证或处理中：有跳转地址时记 3DS 交互并把指令交给本次响应。 */
    ActionResult pending(PayContext context, ChannelExecutionResult channel, Instant now) {
        var result = result(ActionStatus.PROCESSING, channel.getChannelStatus(), channel.getFailureMessage(), now);
        String redirectUrl = channel.getRedirectUrl();
        if (redirectUrl != null) {
            var expiresAt = now.plus(INTERACTION_WINDOW);
            result.setInteractionType(InteractionType.THREE_DS_CHALLENGE);
            result.setInteractionExpiresAt(expiresAt);
            context.setNextAction(new PayNextAction(InteractionType.THREE_DS_CHALLENGE, redirectUrl, expiresAt));
        }
        return result;
    }

    /** 渠道明确拒绝或已拒绝的交易：失败代码取自渠道结论。 */
    static ActionResult rejected(ChannelExecutionResult channel, Instant now) {
        return failed(channel.getChannelStatus(), channel.getFailureMessage(), now, channel.getFailureCode());
    }

    /** 明确失败：记录安全的失败代码与说明；未发出请求时没有渠道状态与说明。 */
    static ActionResult failed(String channelStatus, String failureMessage, Instant now, String code) {
        var result = result(ActionStatus.FAILED, channelStatus, failureMessage, now);
        result.setChannelResponseCode(safeCode(code));
        return result;
    }

    /** 公共结果字段：渠道原始状态作为响应代码，失败说明截断到列长。 */
    static ActionResult result(ActionStatus status, String channelStatus, String failureMessage, Instant now) {
        var result = new ActionResult();
        result.setStatus(status);
        result.setChannelResponseCode(safeCode(channelStatus));
        String message = failureMessage;
        result.setChannelResponseMsg(message == null ? null : message.substring(0, Math.min(message.length(), 512)));
        result.setChannelResultTime(now);
        return result;
    }

    /** 响应代码列为 ASCII、最长 128；不合规的代码不保存。 */
    static String safeCode(String code) {
        return code != null && code.matches("[\\x21-\\x7E]{1,128}") ? code : null;
    }
}
