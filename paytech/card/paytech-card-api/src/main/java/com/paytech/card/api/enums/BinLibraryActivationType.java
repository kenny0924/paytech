package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库激活类型的稳定代码。 */
@Getter
@AllArgsConstructor
public enum BinLibraryActivationType implements CodeEnum {
    /** 激活校验通过（VALID）的版本。 */
    ACTIVATE("ACTIVATE"),

    /** 重新激活已停用（RETIRED）的版本，即回切。 */
    ROLLBACK("ROLLBACK");

    /** 稳定代码 */
    private final String code;
}
