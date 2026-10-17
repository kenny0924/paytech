package com.paytech.payment.execution.core.action.flow;

import static com.paytech.payment.execution.core.action.ActionType.*;

import java.time.Clock;
import java.util.List;

import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.execution.core.enums.InstrumentType;
import com.paytech.payment.execution.core.enums.RiskResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.payment.execution.core.action.ActionFactory;
import com.paytech.payment.execution.core.action.flow.condition.ActionStatusCondition;
import com.paytech.payment.execution.core.action.flow.condition.AuthenticationRequiredCondition;
import com.paytech.payment.execution.core.action.flow.condition.CaptureModeCondition;
import com.paytech.payment.execution.core.action.flow.condition.RiskResultCondition;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.ChannelTransactionRefRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

/** CARD + PAY：授权或直接收款完成后结束本次流程 */
@Component
public class CardPayFlow extends ActionFlow<PayContext> {
    /** 注册唯一的卡支付流程定义。 */
    public CardPayFlow(ActionFactory actionFactory, TransactionTemplate transactionTemplate,
        PaymentActionRepository paymentActionRepository, PaymentAttemptRepository paymentAttemptRepository, Clock clock,
        PaymentIntentRepository intents, ChannelTransactionRefRepository refs) {
        super(InstrumentType.CARD.getCode(), "PAY", PAY_RISK, defineTransitions(), actionFactory, transactionTemplate,
            paymentActionRepository, paymentAttemptRepository, clock, intents, refs);
    }

    /** 条件对象组合定义业务分支；风控评估完成但未放行时 Attempt 失败，Capture/Void 属于后续独立入口。 */
    static List<ActionTransition<PayContext>> defineTransitions() {
        var succeeded = new ActionStatusCondition(ActionStatus.SUCCEEDED);
        var failed = new ActionStatusCondition(ActionStatus.FAILED);
        var allowed = succeeded.and(new RiskResultCondition(RiskResult.ALLOW));
        var notAllowed = succeeded.and(new RiskResultCondition(RiskResult.ALLOW).negate());
        var authentication = new AuthenticationRequiredCondition();
        var manual = new CaptureModeCondition(CaptureMode.MANUAL);
        var automatic = new CaptureModeCondition(CaptureMode.AUTOMATIC);
        return List.of(ActionTransition.next(PAY_RISK, allowed.and(authentication), AUTHENTICATE),
            ActionTransition.next(PAY_RISK, allowed.and(authentication.negate()).and(manual), AUTHORIZE),
            ActionTransition.next(PAY_RISK, allowed.and(authentication.negate()).and(automatic), PAY),
            ActionTransition.fail(PAY_RISK, notAllowed), ActionTransition.end(PAY_RISK, failed),
            ActionTransition.next(AUTHENTICATE, succeeded.and(manual), AUTHORIZE),
            ActionTransition.next(AUTHENTICATE, succeeded.and(automatic), PAY),
            ActionTransition.end(AUTHENTICATE, failed), ActionTransition.end(AUTHORIZE, succeeded.or(failed)),
            ActionTransition.end(PAY, succeeded.or(failed)));
    }
}
