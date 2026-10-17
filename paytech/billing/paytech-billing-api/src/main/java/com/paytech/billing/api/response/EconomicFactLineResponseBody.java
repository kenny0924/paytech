package com.paytech.billing.api.response;

import lombok.Getter;
import lombok.Setter;

/** 经济事实明细行。 */
@Getter
@Setter
public class EconomicFactLineResponseBody {
    /** 行号，从 1 起 */
    private String lineNo;

    /** 费用类型代码 */
    private String feeTypeCode;

    /** 付方角色；规则计算产生的交易事实行可为空 */
    private String payerRole;

    /** 付方标识 */
    private String payerId;

    /** 收方角色；可空条件同 payerRole */
    private String payeeRole;

    /** 收方标识 */
    private String payeeId;

    /** 按币种精度补齐的金额 */
    private String amount;

    /** 影响方向：CHARGE、REVERSAL、ADJUSTMENT */
    private String direction;
}
