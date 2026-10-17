package com.paytech.payment.execution.core.action.flow.condition;

import java.util.function.BiPredicate;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.execution.domain.PayContext;

import lombok.AllArgsConstructor;

/** 判断本次尝试的捕获模式 */
@AllArgsConstructor
public final class CaptureModeCondition implements BiPredicate<PayContext, PaymentAction> {
    /** 期望值 */
    private final CaptureMode mode;

    /** 比较已构建上下文中的业务值。 */
    @Override
    public boolean test(PayContext context, PaymentAction action) {
        return context.getAttempt().getCaptureMode() == mode;
    }
}
