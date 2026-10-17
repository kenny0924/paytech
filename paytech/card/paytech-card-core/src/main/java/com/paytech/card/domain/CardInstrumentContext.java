package com.paytech.card.domain;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** Card 工具处理上下文，不进入 Execution 持久化层 */
@Getter
@Setter
public class CardInstrumentContext {
    /** Client ID */
    private Long clientId;

    /** 支付意图 ID */
    private Long intentId;

    /** 来源提交 ID */
    private Long sourceSubmissionId;

    /** 精确支付方式代码 */
    private String paymentMethodCode;

    /** 本次卡输入；用已存卡时只含 CVV */
    private CardInput card;

    /** 用已存卡支付时的对外已存卡 Token；手输卡为空 */
    private String storedCardToken;

    /** 本次支付关联的 Commerce Customer；访客支付为空 */
    private Long customerId;

    /** 消费者是否同意支付成功后保存卡；只用于手输卡且必须有 Customer */
    private boolean saveCard;

    /** Card 稳定记录引用，申请成功后填写，重放时传原值 */
    private String instrumentRef;

    /** 工具期限上限，Card 可以缩短，不能延长 */
    private Instant expiresAt;

    /** 新申请在事务外准备的卡交易工具，由 Card 在调用方事务内保存；原提交为空 */
    private PreparedInstrument preparedInstrument;

    /** 构造本次卡工具处理上下文；准备结果由 Card 填写 */
    public CardInstrumentContext(Long clientId, Long intentId, Long sourceSubmissionId, String paymentMethodCode,
        CardInput card, String instrumentRef, Instant expiresAt) {
        this.clientId = clientId;
        this.intentId = intentId;
        this.sourceSubmissionId = sourceSubmissionId;
        this.paymentMethodCode = paymentMethodCode;
        this.card = card;
        this.instrumentRef = instrumentRef;
        this.expiresAt = expiresAt;
    }

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
