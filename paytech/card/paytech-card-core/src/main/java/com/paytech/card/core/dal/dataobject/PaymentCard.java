package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** t_payment_card 的完整持久化映射；不通过 HTTP 或模块边界暴露。 */
@Getter
@Setter
public class PaymentCard {
    /** 卡标识。 */
    private Long paymentCardId;

    /** 卡指纹，全表唯一。 */
    private String cardFingerprint;

    /** 卡 BIN，6 至 8 位。 */
    private String cardBin;

    /** 卡号后 4 位。 */
    private String panLast4;

    /** 本卡 DEK 加密的卡号。 */
    private byte[] encryptedPan;

    /** 本卡 DEK 的 Vault Token。 */
    private String dekVaultToken;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "PaymentCard[" + paymentCardId + "]";
    }
}
