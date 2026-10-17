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
    /** 结束时将 Attempt 置为失败并释放 Intent；仅对无目标动作的规则有效 */
    private final boolean attemptFailed;

    /** 构造只读转移规则。 */
    private ActionTransition(ActionType fromAction, BiPredicate<T, PaymentAction> condition, ActionType toAction,
        boolean attemptFailed) {
        this.fromAction = Objects.requireNonNull(fromAction, "来源动作必填");
        this.condition = Objects.requireNonNull(condition, "转移条件必填");
        this.toAction = toAction;
        this.attemptFailed = attemptFailed;
    }

    /** 条件成立后进入下一动作。 */
    public static <T extends ActionContext> ActionTransition<T> next(ActionType from,
        BiPredicate<T, PaymentAction> condition, ActionType to) {
        return new ActionTransition<>(from, condition, Objects.requireNonNull(to, "目标动作必填"), false);
    }

    /** 条件成立后结束当前操作，不直接决定 Attempt 的状态。 */
    public static <T extends ActionContext> ActionTransition<T> end(ActionType from,
        BiPredicate<T, PaymentAction> condition) {
        return new ActionTransition<>(from, condition, null, false);
    }

    /**
     * 条件成立后结束当前操作，并将 Attempt 置为失败、释放 Intent
     * <li>用于动作本身已成功但业务结果不允许继续的情况，例如风控评估完成但未放行。</li>
     */
    public static <T extends ActionContext> ActionTransition<T> fail(ActionType from,
        BiPredicate<T, PaymentAction> condition) {
        return new ActionTransition<>(from, condition, null, true);
    }
}
