package com.paytech.payment.execution.core.action;

import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;

/**
 * @author lzb
 */
public interface ActionContext {
    /** 当前支付意图。 */
    PaymentIntent getIntent();

    /** 当前动作所属的支付尝试。 */
    PaymentAttempt getAttempt();
}
