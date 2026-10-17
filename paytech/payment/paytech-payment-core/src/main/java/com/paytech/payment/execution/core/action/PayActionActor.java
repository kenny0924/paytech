package com.paytech.payment.execution.core.action;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;

import org.springframework.stereotype.Component;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.payment.card.service.CardService;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.domain.*;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentChannelClient;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;

/**
 * 直接收款（PayerMax 卡直付对接技术方案第 5 节）：经 Channel 端口以冻结坐标调用 Platform 标准 API PAY，卡只以支付工具 Token 传递。
 *
 * <p>
 * 结果映射：SUCCESS + SUCCEEDED → 成功；SUCCESS + PENDING → 处理中（有跳转时记 3DS 交互，指令只放入本次 Context）；SUCCESS + FAILED、FAIL、 NOT_SENT →
 * 失败；ERROR → 未知。恢复（渠道通知与查询）本期不做，PROCESSING/UNKNOWN 保持原状；不重发扣款。
 */
@Component
public class PayActionActor extends AbstractActionActor<PayContext> {
    /** 标准 API 编码。 */
    static final String STANDARD_API = "PAY";

    /** 3DS 等交互的有效期上限；渠道订单最短有效期 1800 秒。 */
    static final Duration INTERACTION_WINDOW = Duration.ofMinutes(30);

    private final Clock clock;
    private final PaymentChannelClient paymentChannelClient;
    private final CardService cardService;
    private final CompensationEventRegistrar events;

    /** 注入 Channel 端口、收款结果持久化、Card 本地能力与补偿任务登记。 */
    public PayActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts, Clock clock,
        PaymentChannelClient paymentChannelClient, CardService cardService, CompensationEventRegistrar events) {
        super(intents, attempts);
        this.clock = clock;
        this.paymentChannelClient = paymentChannelClient;
        this.cardService = cardService;
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
        ChannelExecutionResult channel = paymentChannelClient.execute(command(context));
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return switch (channel.getOutcome()) {
            case SUCCESS -> accepted(context, PayOutputs.of(channel.getOutputs()), now);
            case FAIL -> rejected(PayOutputs.of(channel.getOutputs()), now);
            case NOT_SENT -> failed(PayOutputs.empty(), now, channel.getFailureCode());
            case ERROR -> ActionResult.unknown();
        };
    }

    /**
     * 收款成功后完成 Attempt 和 Intent；卡工具同事务交 Card 标记凭证已验证并按同意存卡（只写本库）。
     * <li>同事务登记 PaymentSucceeded 发布任务，提交后由补偿 Handler 发布；事务回滚时任务一并回滚。</li>
     */
    @Override
    public void doInTransaction(ActionContext context, PaymentAction action) {
        assert action.statusIn(ActionStatus.SUCCEEDED);
        var attempt = context.getAttempt();
        attempts.payCompleted(attempt, action.getAmount());
        intents.attemptSucceed(context.getIntent(), attempt.getPaymentAttemptId(), IntentStatus.SUCCEEDED);
        var instToken = ((PayContext)context).getInstToken();
        if (instToken.getInstrumentType() == InstrumentType.CARD) {
            cardService.paymentSucceeded(instToken.getInstrumentRef());
        }
        events.register(PaymentSucceededHandler.buildEvent(attempt.getPaymentAttemptId()));
    }

    /** 以冻结坐标与新支付维度组装渠道动作；输入不含卡值。 */
    static ChannelExecutionCommand command(PayContext context) {
        var intent = context.getIntent();
        var attempt = context.getAttempt();
        var command = new ChannelExecutionCommand();
        command.setStandardApiCode(STANDARD_API);
        command.setOperation(ActionType.PAY.getCode());
        command.setEntityNo(intent.getEntityNo());
        command.setChannelId(attempt.getChannelId());
        command.setChannelAccountId(attempt.getChannelAccountId());
        command.setMidId(attempt.getMidId());
        command.setPaymentMethodCode(attempt.getPaymentMethodCode());
        command.setCountryCode(intent.getConsumerCountryCode());
        command.setCurrency(intent.getCurrency());
        command.setMccCode(intent.getMccCode());
        command.setAmount(intent.getAmount());
        command.setInput(input(context).toMap());
        return command;
    }

    /** 标准 API PAY 输入：支付工具 Token、本次尝试与金额、回跳地址、付款人引用及可选网络信息。 */
    static PayInput input(PayContext context) {
        var intent = context.getIntent();
        var channelInput = context.getChannelInput();
        var input = new PayInput();
        input.setCardToken(context.getInstToken().getPaymentInstrumentToken());
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

    /** 渠道已受理：按支付状态映射；不认识的状态视为未知。 */
    ActionResult accepted(PayContext context, PayOutputs outputs, Instant now) {
        String status = outputs.getPaymentStatus();
        ActionResult result = switch (status == null ? "" : status) {
            case PayOutputs.STATUS_SUCCEEDED -> result(ActionStatus.SUCCEEDED, outputs, now);
            case PayOutputs.STATUS_PENDING -> pending(context, outputs, now);
            case PayOutputs.STATUS_FAILED -> rejected(outputs, now);
            default -> ActionResult.unknown();
        };
        result.setChannelTransactionId(outputs.getChannelTransactionRef());
        return result;
    }

    /** 待认证或处理中：有跳转地址时记 3DS 交互并把指令交给本次响应。 */
    ActionResult pending(PayContext context, PayOutputs outputs, Instant now) {
        var result = result(ActionStatus.PROCESSING, outputs, now);
        String redirectUrl = outputs.getRedirectUrl();
        if (redirectUrl != null) {
            var expiresAt = now.plus(INTERACTION_WINDOW);
            result.setInteractionType(InteractionType.THREE_DS_CHALLENGE);
            result.setInteractionExpiresAt(expiresAt);
            context.setNextAction(new PayNextAction(InteractionType.THREE_DS_CHALLENGE, redirectUrl, expiresAt));
        }
        return result;
    }

    /** 渠道明确拒绝或已拒绝的交易：失败代码取自输出。 */
    static ActionResult rejected(PayOutputs outputs, Instant now) {
        return failed(outputs, now, outputs.getFailureCode());
    }

    /** 明确失败：记录安全的失败代码与说明。 */
    static ActionResult failed(PayOutputs outputs, Instant now, String code) {
        var result = result(ActionStatus.FAILED, outputs, now);
        result.setChannelResponseCode(safeCode(code));
        return result;
    }

    /** 公共结果字段：渠道原始状态作为响应代码，失败说明截断到列长。 */
    static ActionResult result(ActionStatus status, PayOutputs outputs, Instant now) {
        var result = new ActionResult();
        result.setStatus(status);
        result.setChannelResponseCode(safeCode(outputs.getChannelStatus()));
        String message = outputs.getFailureMessage();
        result.setChannelResponseMsg(message == null ? null : message.substring(0, Math.min(message.length(), 512)));
        result.setChannelResultTime(now);
        return result;
    }

    /** 响应代码列为 ASCII、最长 128；不合规的代码不保存。 */
    static String safeCode(String code) {
        return code != null && code.matches("[\\x21-\\x7E]{1,128}") ? code : null;
    }
}
