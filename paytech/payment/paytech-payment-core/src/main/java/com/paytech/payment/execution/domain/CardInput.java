package com.paytech.payment.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 卡输入：只在 card-tokens 入口到 card-service 的本次调用中传递（design/05 §6），不进入 Execution 持久化层、日志或 MQ
 * <li>用已存卡时只含本次 CVV。</li>
 */
@Getter
@Setter
public class CardInput {
    /** 卡号 */
    private String pan;

    /** 有效期月份 */
    private String expiryMonth;

    /** 有效期年份 */
    private String expiryYear;

    /** 持卡人姓名 */
    private String cardholderName;

    /** 安全码，不持久化、不哈希 */
    private String cvv;

    @Override
    public String toString() {
        return "CardInput[REDACTED]";
    }
}
