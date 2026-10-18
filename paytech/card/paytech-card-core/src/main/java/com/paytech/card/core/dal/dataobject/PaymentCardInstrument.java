package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_payment_card_instrument 的完整持久化映射；不通过 HTTP 或模块边界暴露。 */
@Getter
@Setter
public class PaymentCardInstrument {
    /** 卡交易工具标识，即工具引用。 */
    private Long paymentCardInstrumentId;

    /** 本次使用的卡凭证标识。 */
    private Long paymentCardCredentialId;

    /** Client 标识。 */
    private Long clientId;

    /** 申请本工具的 Commerce 来源提交标识；与 Client 组成幂等键。 */
    private Long sourceSubmissionId;

    /** 关联的 Commerce Customer 标识；访客支付为空。 */
    private Long customerId;

    /** 消费者是否同意支付成功后保存卡。 */
    private boolean saveRequested;

    /** 用已存卡支付时指向的已存卡；手输卡为空。 */
    private Long paymentStoredCardId;

    /** 首次用卡资格截止时间，等于 CVV 暂存期限。 */
    private Instant expiresAt;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "PaymentCardInstrument[" + paymentCardInstrumentId + "]";
    }
}
