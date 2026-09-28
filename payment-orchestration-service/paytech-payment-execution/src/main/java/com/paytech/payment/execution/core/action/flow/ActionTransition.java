package com.paytech.payment.execution.core.action.flow;

import java.util.Objects;
import java.util.function.BiPredicate;

import com.paytech.payment.execution.core.action.ActionContext;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;

import lombok.Getter;

/** 一条动作转移规则 */
@Getter
public final class ActionTransition<T extends ActionContext> {
    /** 来源动作 */
    private final ActionType fromAction;
    /** 分支条件 */
    private final BiPredicate<T, PaymentAction> condition;
    /** 目标动作；为空表示显式结束本次 Flow */
    private final ActionType toAction;

    /** 构造只读转移规则。 */
    private ActionTransition(ActionType fromAction, BiPredicate<T, PaymentAction> condition, ActionType toAction) {
        this.fromAction = Objects.requireNonNull(fromAction, "来源动作必填");
        this.condition = Objects.requireNonNull(condition, "转移条件必填");
        this.toAction = toAction;
    }

    /** 条件成立后进入下一动作。 */
    public static <T extends ActionContext> ActionTransition<T> next(ActionType from,
        BiPredicate<T, PaymentAction> condition, ActionType to) {
        return new ActionTransition<>(from, condition, Objects.requireNonNull(to, "目标动作必填"));
    }

    /** 条件成立后结束当前操作，不直接决定 Attempt 的状态。 */
    public static <T extends ActionContext> ActionTransition<T> end(ActionType from,
        BiPredicate<T, PaymentAction> condition) {
        return new ActionTransition<>(from, condition, null);
    }
}
