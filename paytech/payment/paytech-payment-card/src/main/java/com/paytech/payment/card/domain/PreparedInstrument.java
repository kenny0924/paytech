package com.paytech.payment.card.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 事务外已准备、待在调用方事务内保存的卡交易工具
 * <li>卡与卡凭证已在准备阶段保存；这里只剩本次交易的工具记录。</li>
 * <li>只在 Card 内读取；调用方只负责把所在的上下文原样交回 Card 保存。</li>
 */
@Getter
@AllArgsConstructor
public class PreparedInstrument {
    /** 已分配的卡交易工具 ID，即工具引用 */
    private final long paymentCardInstrumentId;

    /** 本次使用的卡凭证 ID */
    private final long paymentCardCredentialId;

    /** 用已存卡支付时的已存卡 ID；手输卡为空 */
    private final Long paymentStoredCardId;

    @Override
    public String toString() {
        return "PreparedInstrument[" + paymentCardInstrumentId + "]";
    }
}
