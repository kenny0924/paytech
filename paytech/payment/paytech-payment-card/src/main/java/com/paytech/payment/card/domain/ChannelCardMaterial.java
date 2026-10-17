package com.paytech.payment.card.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道执行用卡材料；只在本次调用内存中传递，不持久化、不记录日志。 */
@Getter
@AllArgsConstructor
public class ChannelCardMaterial {
    /** 卡号 */
    private final String pan;

    /** 卡有效期月份，01–12 */
    private final String expiryMonth;

    /** 卡有效期年份，四位数字 */
    private final String expiryYear;

    /** 持卡人姓名 */
    private final String cardholderName;

    /** 安全码，领取后即从 Card 暂存中清除 */
    private final String cvv;

    @Override
    public String toString() {
        return "ChannelCardMaterial[REDACTED]";
    }
}
