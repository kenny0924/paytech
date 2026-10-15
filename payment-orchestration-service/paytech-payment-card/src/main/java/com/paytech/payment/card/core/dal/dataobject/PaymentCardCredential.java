package com.paytech.payment.card.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_payment_card_credential 的完整持久化映射；不通过 HTTP 或模块边界暴露。 */
@Getter
@Setter
public class PaymentCardCredential {
    /** 卡凭证标识。 */
    private Long paymentCardCredentialId;

    /** 所属卡标识。 */
    private Long paymentCardId;

    /** 凭证摘要，同一张卡下唯一。 */
    private String credentialDigest;

    /** 本卡 DEK 加密的有效期与持卡人原值。 */
    private byte[] encryptedDetails;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "PaymentCardCredential[" + paymentCardCredentialId + "]";
    }
}
