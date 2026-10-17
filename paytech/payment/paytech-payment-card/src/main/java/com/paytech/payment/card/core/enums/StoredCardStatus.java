package com.paytech.payment.card.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已存卡状态的数据库稳定代码。 */
@Getter
@AllArgsConstructor
public enum StoredCardStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    DELETED("DELETED");

    /** 稳定代码 */
    private final String code;
}
