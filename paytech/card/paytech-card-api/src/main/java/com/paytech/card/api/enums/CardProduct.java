package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 卡产品类别的稳定代码。 */
@Getter
@AllArgsConstructor
public enum CardProduct implements CodeEnum {
    /** 个人卡 */
    CONSUMER("CONSUMER"),

    /** 商务卡 */
    COMMERCIAL("COMMERCIAL");

    /** 稳定代码 */
    private final String code;
}
