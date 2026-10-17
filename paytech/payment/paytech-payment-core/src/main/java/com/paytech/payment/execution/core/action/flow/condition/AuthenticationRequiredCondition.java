package com.paytech.payment.execution.core.action.flow.condition;

import java.util.function.BiPredicate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.domain.PayContext;

/** 风控明确要求付款人认证 */
public final class AuthenticationRequiredCondition implements BiPredicate<PayContext, PaymentAction> {
    /** 未给出认证要求时拒绝判定，不能默认跳过认证。 */
    @Override
    public boolean test(PayContext context, PaymentAction action) {
        if (action.getAuthenticationRequirement() == null) {
            throw new ErrorException("风控允许结果缺少认证要求");
        }
        return switch (action.getAuthenticationRequirement()) {
            case NOT_REQUIRED -> false;
            case THREE_DS_REQUIRED, CHALLENGE_REQUIRED -> true;
        };
    }
}
