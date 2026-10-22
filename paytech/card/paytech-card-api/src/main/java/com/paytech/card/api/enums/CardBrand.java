package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 卡组织（主品牌）的稳定代码
 * <li>与 commercial CARD_BRAND 规范值、MID 能力 scheme_code 同一套（BIN 库需求 E6）；共同品牌卡只记主品牌。</li>
 */
@Getter
@AllArgsConstructor
public enum CardBrand implements CodeEnum {
    /** Visa */
    VISA("VISA"),

    /** Mastercard */
    MASTERCARD("MASTERCARD"),

    /** American Express */
    AMEX("AMEX"),

    /** 银联 */
    UNIONPAY("UNIONPAY"),

    /** mada（沙特本地卡组织） */
    MADA("MADA"),

    /** Discover */
    DISCOVER("DISCOVER"),

    /** JCB */
    JCB("JCB"),

    /** Diners Club */
    DINERS("DINERS");

    /** 稳定代码 */
    private final String code;
}
