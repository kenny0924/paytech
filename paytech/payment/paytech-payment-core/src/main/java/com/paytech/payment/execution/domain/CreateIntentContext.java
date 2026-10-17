package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建支付意图上下文 */
@Getter
@AllArgsConstructor
public class CreateIntentContext {
    /** 支付意图 */
    private final PaymentIntent intent;
}
