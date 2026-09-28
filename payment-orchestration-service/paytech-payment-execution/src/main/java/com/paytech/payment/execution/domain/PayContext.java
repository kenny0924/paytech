package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.action.ActionContext;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;

import lombok.Getter;
import lombok.Setter;

/** 支付受理与动作执行上下文 */
@Getter
@Setter
public class PayContext implements ActionContext {
    public PayContext(PaymentIntent intent, PaymentAttempt attempt, PaymentInstrumentToken instToken) {
        this.intent = intent;
        this.attempt = attempt;
        this.instToken = instToken;
    }

    /**
     * 支付意图
     * <li>已校验 Client 归属，提供金额、币种和 OE 等冻结事实。</li>
     */
    private PaymentIntent intent;

    /**
     * 支付尝试
     * <li>新建尝试或原提交绑定的尝试。</li>
     */
    private PaymentAttempt attempt;

    /**
     * 支付工具
     * <li>用于新提交校验或原提交内容比对。</li>
     */
    private PaymentInstrumentToken instToken;

    /** 当前工具类型对应的支付入口流程。 */
    public String actionFlowKey() {
        return instToken.getInstrumentType().getCode() + ":PAY";
    }
}
