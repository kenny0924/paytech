package com.paytech.payment.execution.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.payment.execution.core.action.ActionResult;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.action.flow.ActionFlow;
import com.paytech.payment.execution.core.action.flow.ActionFlowFactory;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.domain.PayContext;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.service.ActionExecutionService;

import lombok.AllArgsConstructor;

/** 动作创建、执行与结果保存；同一 Context 贯穿流程 */
@Service
@AllArgsConstructor
public class ActionExecutionServiceImpl implements ActionExecutionService {
    private final Clock clock;
    private final PaymentActionRepository actions;
    private final PaymentAttemptRepository attempts;
    private final ActionFlowFactory actionFlowFactory;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ActionResult execute(PayContext context) {
        ActionFlow<PayContext> flow = actionFlowFactory.getFlow(context.actionFlowKey());
        Long activeActionId = context.getAttempt().getActiveActionId();
        PaymentAction action;
        if (activeActionId != null) {
            action = actions.selectById(activeActionId);
        } else {
            action = buildAndSaveAction(context, flow.getStart());
        }
        return flow.execute(context, action);
    }

    PaymentAction buildAndSaveAction(PayContext context, ActionType type) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentAction action = new PaymentAction();
        action.setPaymentAttemptId(context.getAttempt().getPaymentAttemptId());
        action.setActionNo(1);
        action.setActionType(type);
        action.setActionStatus(ActionStatus.PROCESSING);
        action.setAmount(context.getIntent().getAmount());
        action.setCurrency(context.getIntent().getCurrency());
        action.setVersion(1L);
        action.setCreateTime(now);
        action.setUpdateTime(now);
        transactionTemplate.executeWithoutResult(status -> {
            actions.insertAction(action);
            attempts.bindAction(context.getAttempt(), action.getPaymentActionId());
        });
        return action;
    }
}
