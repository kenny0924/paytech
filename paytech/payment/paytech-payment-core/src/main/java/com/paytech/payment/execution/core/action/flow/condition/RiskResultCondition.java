package com.paytech.payment.execution.core.action.flow.condition;

import java.util.function.BiPredicate;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.domain.PayContext;

import lombok.AllArgsConstructor;

/** 判断当前风控结论 */
@AllArgsConstructor
public final class RiskResultCondition implements BiPredicate<PayContext, PaymentAction> {
    /** 期望值 */
    private final RiskResult result;

    /** 比较已构建上下文中的业务值。 */
    @Override
    public boolean test(PayContext context, PaymentAction action) {
        return action.getRiskResult() == result;
    }
}
