package com.paytech.card.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 风控卡属性：只在本次评估请求内存中传递，不持久化、不记录日志，不含卡号。 */
@Getter
@AllArgsConstructor
public class CardRiskAttributes {
    /** 卡 BIN，6 至 8 位数字 */
    private final String bin;

    /**
     * 跨交易稳定的卡指纹
     * <li>同一卡号在不同交易中相同，不能反推卡号；受限值，只经内部同步请求传给 Risk。</li>
     */
    private final String fingerprint;

    @Override
    public String toString() {
        return "CardRiskAttributes[REDACTED]";
    }
}
