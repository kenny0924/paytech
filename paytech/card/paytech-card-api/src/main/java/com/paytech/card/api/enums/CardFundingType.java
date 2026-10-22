package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 借贷记类型的稳定代码。 */
@Getter
@AllArgsConstructor
public enum CardFundingType implements CodeEnum {
    /** 信用卡 */
    CREDIT("CREDIT"),

    /** 借记卡 */
    DEBIT("DEBIT"),

    /** 预付卡 */
    PREPAID("PREPAID"),

    /** 签账卡（按期全额还款，如部分 AMEX） */
    CHARGE("CHARGE");

    /** 稳定代码 */
    private final String code;
}
