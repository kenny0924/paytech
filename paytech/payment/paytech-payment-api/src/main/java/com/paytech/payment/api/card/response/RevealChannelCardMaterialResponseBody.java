package com.paytech.payment.api.card.response;

import lombok.Getter;
import lombok.Setter;

/** 渠道执行读卡响应；全部字段为卡原值，只在调用方内存中使用 */
@Getter
@Setter
public class RevealChannelCardMaterialResponseBody {
    /** 卡号，12–19 位数字 */
    private String pan;

    /** 卡有效期月份，01–12 */
    private String expiryMonth;

    /** 卡有效期年份，四位数字 */
    private String expiryYear;

    /** 持卡人姓名 */
    private String cardholderName;

    /**
     * 安全码
     * <li>3–4 位数字；只能领取一次，已领取或不需要时为 null。</li>
     */
    private String cvv;

    @Override
    public String toString() {
        return "RevealChannelCardMaterialResponseBody[REDACTED]";
    }
}
