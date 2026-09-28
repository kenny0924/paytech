package com.paytech.payment.card.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Card 工具处理上下文，不进入 Execution 持久化层 */
@Getter
@Setter
@AllArgsConstructor
public class CardInstrumentContext {
    /** Client ID */
    private Long clientId;

    /** 支付意图 ID */
    private Long intentId;

    /** 来源提交 ID */
    private Long sourceSubmissionId;

    /** 精确支付方式代码 */
    private String paymentMethodCode;

    /** 本次卡输入 */
    private CardInput card;

    /** Card 稳定记录引用，申请成功后填写，重放时传原值 */
    private String instrumentRef;

    /** 工具期限上限，Card 可以缩短，不能延长 */
    private Instant expiresAt;

    /** 卡输入，只在本次调用中传递 */
    @Getter
    @Setter
    public static class CardInput {
        /** 卡号 */
        private String pan;

        /** 有效期月份 */
        private String expiryMonth;

        /** 有效期年份 */
        private String expiryYear;

        /** 持卡人姓名 */
        private String cardholderName;

        /** 安全码，不参与身份比较或持久化 */
        private String cvv;

        @Override
        public String toString() {
            return "CardInput[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "CardInstrumentContext[REDACTED]";
    }
}
