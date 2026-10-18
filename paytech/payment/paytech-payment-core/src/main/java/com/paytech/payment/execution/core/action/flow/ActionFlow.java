package com.paytech.payment.execution.core.action.flow;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.action.*;
import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.domainservice.repository.ChannelTransactionRefRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;

import lombok.Getter;

/** 一次入口操作的动作流 */
@Getter
public abstract class ActionFlow<T extends ActionContext> {
    /** 开始动作 */
    final ActionType start;
    /** 支付工具类型 */
    final String instrumentType;
    /** 入口操作 */
    final String operation;
    /** 动作实现工厂 */
    final ActionFactory actionFactory;
    /** 转移规则 */
    final List<ActionTransition<T>> transitions;
    /** 结果事务 */
    final TransactionTemplate transactionTemplate;
    /** 动作记录 */
    final PaymentActionRepository actions;
    /** 支付尝试与执行位 */
    final PaymentAttemptRepository attempts;
    final PaymentIntentRepository intents;
    /** 渠道交易引用 */
    final ChannelTransactionRefRepository refs;
    /** 业务时钟 */
    final Clock clock;

    /** 构造流程并检查节点引用，运行期间不修改定义。 */
    public ActionFlow(String instrumentType, String operation, ActionType start, List<ActionTransition<T>> transitions,
        ActionFactory actionFactory, TransactionTemplate transactionTemplate, PaymentActionRepository actions,
        PaymentAttemptRepository attempts, Clock clock, PaymentIntentRepository intents, ChannelTransactionRefRepository refs) {
        this.instrumentType = Objects.requireNonNull(instrumentType, "支付工具类型必填");
        this.operation = Objects.requireNonNull(operation, "入口操作必填");
        this.start = Objects.requireNonNull(start, "开始动作必填");
        this.transitions = List.copyOf(transitions);
        this.actionFactory = actionFactory;
        this.transactionTemplate = transactionTemplate;
        this.actions = actions;
        this.attempts = attempts;
        this.clock = clock;
        this.intents = intents;
        this.refs = refs;
        Set<ActionType> nodes = actionTypes();
        if (!nodes.contains(start) || this.transitions.stream()
            .anyMatch(rule -> rule.getToAction() != null && !nodes.contains(rule.getToAction()))) {
            throw new IllegalArgumentException("流程入口或目标动作缺少转移定义");
        }
    }

    /** 返回支付工具类型与入口操作组成的流程标识。 */
    public String flowKey() {
        return instrumentType + ":" + operation;
    }

    /**
     * 执行动作
     *
     * @return 执行结果
     */
    public ActionResult execute(T context, PaymentAction action) {
        ActionResult result = null;
        while (action != null && action.isRunning()) {
            // 事务外执行
            ActionActor actor = actionFactory.getAction(action.getActionType());
            result = actor.doAction(context, action);
            // 保存结果并切换执行位
            action = advanceAction(context, actor, action, result);
        }
        return result;
    }

    /** 同事务保存结果、下一动作及执行位，版本冲突时整体回滚。 */
    PaymentAction advanceAction(T context, ActionActor actor, PaymentAction action, ActionResult result) {
        return transactionTemplate.execute(status -> {
            // 保存结果
            actions.saveResult(action, result);
            saveChannelTransaction(context, action, result);
            if (action.isRunning()) {
                return null;
            } else if (action.statusIn(ActionStatus.FAILED)) {
                failAttempt(context);
                return null;
            }
            assert action.statusIn(ActionStatus.SUCCEEDED);
            ActionTransition<T> transition = next(context, action);
            if (transition.isAttemptFailed()) {
                // 动作成功但业务结果不允许继续
                failAttempt(context);
                return null;
            }
            PaymentAction nextAction = null;
            if (transition.getToAction() == null) {
                attempts.actionSuccess(context.getAttempt(), null);
            } else {
                // 还有下一步
                nextAction = buildNextAction(action, transition.getToAction());
                actions.insertAction(nextAction);
                attempts.actionSuccess(context.getAttempt(), nextAction.getPaymentActionId());
            }
            // 没有下一步
            actor.doInTransaction(context, action);
            return nextAction;
        });
    }

    /** 在结果事务内将 Attempt 置为失败并释放 Intent 的活动尝试。 */
    void failAttempt(T context) {
        attempts.updateToFail(context.getAttempt());
        intents.releaseAttempt(context.getIntent());
    }

    /** 渠道已创建交易时，在结果事务内保存渠道交易引用（冻结的渠道与 MID、本动作类型与金额）。 */
    void saveChannelTransaction(T context, PaymentAction action, ActionResult result) {
        if (result.getChannelTransactionId() == null) {
            return;
        }
        var attempt = context.getAttempt();
        var ref = new ChannelTransactionRef();
        ref.setPaymentActionId(action.getPaymentActionId());
        ref.setChannelId(attempt.getChannelId());
        ref.setMidId(attempt.getMidId());
        ref.setReferenceType(action.getActionType());
        ref.setExternalTransactionId(result.getChannelTransactionId());
        ref.setChannelEventTime(result.getChannelResultTime());
        ref.setAmount(action.getAmount());
        ref.setCurrency(action.getCurrency());
        ref.setCreateTime(clock.instant().truncatedTo(ChronoUnit.MICROS));
        refs.insertRef(ref);
    }

    /** 返回需要注册执行实现的全部节点。 */
    public Set<ActionType> actionTypes() {
        return transitions.stream().map(ActionTransition::getFromAction).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 根据终态结果匹配唯一规则。
     * <li>零命中或多命中均为定义错误，不能隐式结束或选择第一个付款分支。</li>
     */
    public ActionTransition<T> next(T context, PaymentAction action) {
        if (!action.isFinal()) {
            throw new ErrorException("未完成动作不能选择后继");
        }
        ActionTransition<T> matched = null;
        for (ActionTransition<T> rule : transitions) {
            if (rule.getFromAction() == action.getActionType() && rule.getCondition().test(context, action)) {
                if (matched != null) {
                    throw new ErrorException("动作转移同时命中多个分支");
                }
                matched = rule;
            }
        }
        if (matched == null) {
            throw new ErrorException("动作终态未匹配任何转移规则");
        }
        return matched;
    }

    /** 构造后继动作，沿用本次业务金额和币种。 */
    PaymentAction buildNextAction(PaymentAction current, ActionType type) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        PaymentAction action = new PaymentAction();
        action.setPaymentAttemptId(current.getPaymentAttemptId());
        action.setActionNo(Math.addExact(current.getActionNo(), 1));
        action.setActionType(type);
        action.setActionStatus(ActionStatus.PROCESSING);
        action.setAmount(current.getAmount());
        action.setCurrency(current.getCurrency());
        action.setChannelIdempotencyKey(UUID.randomUUID().toString());
        action.setVersion(1L);
        action.setCreateTime(now);
        action.setUpdateTime(now);
        return action;
    }

}
