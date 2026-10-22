package com.paytech.payment.execution.core.action;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.domain.PayChannelInput;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentRiskClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 支付风控：以 Risk 统一评估接口（风控点 PAYMENT_ATTEMPT_RISK）评估本次支付尝试。
 *
 * <p>
 * 取得 Risk 已落库的结论即动作成功，保存评估 ID、结论与认证要求，是否放行由 Flow 转移决定。409、网络失败、超时与 5xx 用同一请求 立即重试一次；400、422
 * 与无效响应不重试。仍未取得结论时动作失败（失败关闭），不记为 UNKNOWN、不登记恢复任务：风控在资金动作之前， 失败后消费者可以重新支付（需求 20261015 payment-接入Risk统一评估 Q-02）。
 *
 * <p>
 * 评估输入中的消费者引用、IP、User-Agent 与卡指纹为受限值，只经本次请求传给 Risk，不写日志；卡指纹与 BIN 取自支付工具 Token 行。
 *
 * @author lzb
 */
@Slf4j
@Component
public class PayRiskCheckActionActor extends AbstractActionActor<PayContext> {
    private final PaymentRiskClient paymentRiskClient;

    /** 注入风控端口与结果持久化能力。 */
    public PayRiskCheckActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts,
        PaymentRiskClient paymentRiskClient) {
        super(intents, attempts);
        this.paymentRiskClient = paymentRiskClient;
    }

    /** 返回支付风控动作代码。 */
    @Override
    public String getCode() {
        return ActionType.PAY_RISK.getCode();
    }

    /** 评估本次支付尝试；取得结论即成功，未取得结论则失败关闭，不在此选择后继动作。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        PaymentRiskResult evaluation;
        try {
            evaluation = evaluateWithRetry(command(context, action));
        } catch (FailException | ErrorException failure) {
            log.warn("支付风控未取得结论，失败关闭: attemptId={}, actionId={}, failure={}", action.getPaymentAttemptId(),
                action.getPaymentActionId(), failure.getClass().getSimpleName());
            return failed();
        }
        var result = new ActionResult();
        result.setStatus(ActionStatus.SUCCEEDED);
        result.setTransactionAssessmentId(evaluation.getEvaluationId());
        result.setRiskResult(evaluation.getRiskResult());
        result.setAuthenticationRequirement(evaluation.getAuthenticationRequirement());
        return result;
    }

    /**
     * 评估，409 或依赖不可用时用同一请求重试一次
     * <li>Risk 对同一支付尝试只评估一次：已评估时重放原结论，正在并发处理时返回 409；同一请求重试不会产生第二次评估。</li>
     * <li>400、422 与无效响应属于对接缺陷，重试也不会改变结果，直接抛出。</li>
     */
    PaymentRiskResult evaluateWithRetry(PaymentRiskCommand command) {
        try {
            return paymentRiskClient.evaluate(command);
        } catch (ConcurrentRequestException | ServiceUnavailableException retryable) {
            log.warn("支付风控首次调用未取得结论，重试一次: attemptId={}, actionId={}, failure={}", command.getPaymentAttemptId(),
                command.getRiskActionId(), retryable.getClass().getSimpleName());
            return paymentRiskClient.evaluate(command);
        }
    }

    /** 按 Risk 输入合同组装评估输入：交易事实取冻结的 Intent/Attempt，受限值取本次请求与支付工具 Token 行。 */
    PaymentRiskCommand command(PayContext context, PaymentAction action) {
        var intent = context.getIntent();
        var attempt = context.getAttempt();
        var command = new PaymentRiskCommand();
        command.setPaymentAttemptId(attempt.getPaymentAttemptId());
        command.setPaymentIntentId(intent.getPaymentIntentId());
        command.setRiskActionId(action.getPaymentActionId());
        command.setOrderId(intent.getSourceId());
        command.setClientId(intent.getClientId());
        command.setEntityNo(intent.getEntityNo());
        command.setPaymentMethodCode(attempt.getPaymentMethodCode());
        command.setCaptureMode(attempt.getCaptureMode());
        command.setAmount(intent.getAmount());
        command.setCurrency(intent.getCurrency());
        command.setIndustryCode(intent.getIndustryCode());
        command.setMccCode(intent.getMccCode());
        command.setConsumerCountryCode(intent.getConsumerCountryCode());
        fillConsumer(command, context.getChannelInput());
        fillCard(command, context.getInstToken());
        return command;
    }

    /** 本次提交的消费者引用与网络信息；风控只在新提交中执行，此时输入一定存在。 */
    static void fillConsumer(PaymentRiskCommand command, PayChannelInput input) {
        command.setConsumerReference(input.getConsumerReference());
        command.setClientIpAddress(input.getConsumerIpAddress());
        command.setUserAgent(input.getConsumerUserAgent());
    }

    /**
     * 卡支付时取申请工具时 card 返回并存入 Token 行的 BIN、指纹（design/05 §2.4）与冻结的 BIN 库卡属性（发卡国、卡组织、借贷记），
     * 不再调用 card；非卡支付或缺失时省略。
     */
    static void fillCard(PaymentRiskCommand command, PaymentInstrumentToken token) {
        if (token.getInstrumentType() != InstrumentType.CARD) {
            return;
        }
        command.setIssuerCountryCode(token.getIssuerCountry());
        command.setCardBrand(token.getCardBrand());
        command.setFundingType(token.getFundingType());
        if (token.getCardBin() == null || token.getCardFingerprint() == null) {
            return;
        }
        command.setCardBin(token.getCardBin());
        command.setCardFingerprint(token.getCardFingerprint());
    }

    /** 未取得风控结论：动作失败，风控结果与原因代码均为空（channel_response_code 只属于渠道动作）。 */
    static ActionResult failed() {
        var result = new ActionResult();
        result.setStatus(ActionStatus.FAILED);
        return result;
    }
}
