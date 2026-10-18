package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import com.paytech.card.core.enums.StoredCardStatus;

import lombok.Getter;
import lombok.Setter;

/** t_payment_stored_card 的完整持久化映射；不通过 HTTP 或模块边界暴露。 */
@Getter
@Setter
public class PaymentStoredCard {
    /** 已存卡标识。 */
    private Long paymentStoredCardId;

    /** 对外已存卡 Token。 */
    private String storedCardToken;

    /** Client 标识。 */
    private Long clientId;

    /** Commerce Customer 标识。 */
    private Long customerId;

    /** 卡标识。 */
    private Long paymentCardId;

    /** 有效时等于卡标识，删除后为空。 */
    private Long activeCardId;

    /** 当前使用的已验证卡凭证标识。 */
    private Long paymentCardCredentialId;

    /** 已存卡状态。 */
    private StoredCardStatus storedCardStatus;

    /** 最近一次同意保存的时间。 */
    private Instant consentTime;

    /** 首次保存来源的卡交易工具标识。 */
    private Long sourceCardInstrumentId;

    /** 删除时间。 */
    private Instant deletedTime;

    /** 乐观锁版本。 */
    private Long version;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "PaymentStoredCard[" + paymentStoredCardId + "]";
    }
}
