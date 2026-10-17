package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 卡工具申请上下文 */
@Getter
@AllArgsConstructor
public class ApplyCardInstrumentContext {
    /** 支付意图 */
    private final PaymentIntent intent;

    /** 本次支付工具绑定 */
    private final PaymentInstrumentToken instrumentToken;
}
