package com.paytech.payment.card.core.dal.dataobject;

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

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间。 */
    private Instant updateTime;

    @Override
    public String toString() {
        return "PaymentCardInstrument[" + paymentCardInstrumentId + "]";
    }
}
