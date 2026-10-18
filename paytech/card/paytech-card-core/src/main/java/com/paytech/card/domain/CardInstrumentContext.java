package com.paytech.card.domain;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** Card 工具创建上下文：只在本次创建命令内存中传递，卡输入不持久化、不记录日志 */
@Getter
@Setter
public class CardInstrumentContext {
    /** Client ID */
    private Long clientId;

    /** 来源提交 ID：与 Client 组成两段创建的幂等键 */
    private Long sourceSubmissionId;

    /** 本次卡输入；用已存卡时只含 CVV */
    private CardInput card;

    /** 用已存卡支付时的对外已存卡 Token；手输卡为空 */
    private String storedCardToken;

    /** 本次支付关联的 Commerce Customer；访客支付为空 */
    private Long customerId;

    /** 消费者是否同意支付成功后保存卡；只用于手输卡且必须有 Customer */
    private boolean saveCard;

    /** Card 稳定记录引用，准备后填写；重放时为原工具引用 */
    private String instrumentRef;

    /** 工具期限上限，Card 可以缩短，不能延长 */
    private Instant expiresAt;

    /** 新申请在事务外准备的卡交易工具，随后由 Card 保存；原提交为空 */
    private PreparedInstrument preparedInstrument;

    /** 构造新申请的上下文；准备结果由 Card 填写 */
    public CardInstrumentContext(Long clientId, Long sourceSubmissionId, CardInput card, Instant expiresAt) {
        this.clientId = clientId;
        this.sourceSubmissionId = sourceSubmissionId;
        this.card = card;
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
