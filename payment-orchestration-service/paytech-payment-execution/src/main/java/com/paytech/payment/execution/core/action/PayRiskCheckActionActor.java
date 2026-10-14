package com.paytech.payment.execution.core.action;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentRiskClient;

/**
 * 支付风控（PayerMax 卡直付对接技术方案第 6 节）：经 Risk 端口评估本次支付尝试，返回评估标识、结论与认证要求。
 *
 * <p>
 * Risk 不可用、拒绝调用或结论不完整时失败关闭（Action FAILED，尚未发生任何资金动作），不默认通过。
 *
 * @author lzb
 */
@Component
public class PayRiskCheckActionActor extends AbstractActionActor<PayContext> {
    private final Clock clock;
    private final PaymentRiskClient paymentRiskClient;

    /** 注入风控端口与结果持久化能力。 */
    public PayRiskCheckActionActor(PaymentIntentRepository intents, PaymentAttemptRepository attempts, Clock clock,
        PaymentRiskClient paymentRiskClient) {
        super(intents, attempts);
        this.clock = clock;
        this.paymentRiskClient = paymentRiskClient;
    }

    /** 返回支付风控动作代码。 */
    @Override
    public String getCode() {
        return ActionType.PAY_RISK.getCode();
    }

    /** 按原 Attempt 评估风险，不在此选择后继动作。 */
    @Override
    protected ActionResult execute(PayContext context, PaymentAction action) {
        PaymentRiskResult assessment;
        try {
            assessment = paymentRiskClient.assess(command(context, action));
        } catch (RuntimeException unavailable) {
            return failed("RISK_UNAVAILABLE");
        }
        if (assessment == null || assessment.getTransactionAssessmentId() == null || assessment.getRiskResult() == null
            || assessment.getAuthenticationRequirement() == null) {
            return failed("RISK_RESULT_INVALID");
        }
        var result = new ActionResult();
        result.setStatus(ActionStatus.SUCCEEDED);
        result.setTransactionAssessmentId(assessment.getTransactionAssessmentId());
        result.setRiskResult(assessment.getRiskResult());
        result.setAuthenticationRequirement(assessment.getAuthenticationRequirement());
        return result;
    }

    /** 评估输入只含交易维度。 */
    static PaymentRiskCommand command(PayContext context, PaymentAction action) {
        var intent = context.getIntent();
        var command = new PaymentRiskCommand();
        command.setPaymentAttemptId(context.getAttempt().getPaymentAttemptId());
        command.setPaymentActionId(action.getPaymentActionId());
        command.setClientId(intent.getClientId());
        command.setEntityNo(intent.getEntityNo());
        command.setAmount(intent.getAmount());
        command.setCurrency(intent.getCurrency());
        command.setPaymentMethodCode(context.getAttempt().getPaymentMethodCode());
        command.setCountryCode(intent.getConsumerCountryCode());
        return command;
    }

    /** 风控失败关闭：明确失败，记录原因代码。 */
    ActionResult failed(String code) {
        var result = new ActionResult();
        result.setStatus(ActionStatus.FAILED);
        result.setChannelResponseCode(code);
        result.setChannelResultTime(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return result;
    }
}
