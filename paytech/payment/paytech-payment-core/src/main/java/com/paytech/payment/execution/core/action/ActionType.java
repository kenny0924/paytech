package com.paytech.payment.execution.core.action;

import com.paytech.foundation.common.enums.CodeMessageEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author lzb
 */
@Getter
@AllArgsConstructor
public enum ActionType implements CodeMessageEnum {

    PAY_RISK("PAY_RISK", "支付风控"),

    AUTHENTICATE("AUTHENTICATE", "付款人认证"),

    AUTHORIZE("AUTHORIZE", "资金授权"),

    PAY("PAY", "直接收款"),

    CAPTURE("CAPTURE", "授权捕获"),

    VOID("VOID", "撤销授权"),

    REFUND("REFUND", "退款");

    /** 动作代码 */
    final String code;

    /** 动作含义 */
    final String message;
}
