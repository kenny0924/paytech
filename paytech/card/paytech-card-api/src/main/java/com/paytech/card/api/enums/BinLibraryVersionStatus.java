package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库版本状态的稳定代码：VALIDATING → VALID / INVALID；VALID → ACTIVE；ACTIVE → RETIRED；RETIRED → ACTIVE（回切）。 */
@Getter
@AllArgsConstructor
public enum BinLibraryVersionStatus implements CodeEnum {
    /** 等待扫描、校验与写入号段。 */
    VALIDATING("VALIDATING"),

    /** 校验通过、号段已写入，可以激活。 */
    VALID("VALID"),

    /** 文件或数据校验失败，终态，不能激活。 */
    INVALID("INVALID"),

    /** 当前生效版本，同一时刻至多一个。 */
    ACTIVE("ACTIVE"),

    /** 曾经生效、已被其他版本替换，可再次激活（回切）。 */
    RETIRED("RETIRED");

    /** 稳定代码 */
    private final String code;
}
