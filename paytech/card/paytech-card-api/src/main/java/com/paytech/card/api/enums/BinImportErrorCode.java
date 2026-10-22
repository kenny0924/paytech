package com.paytech.card.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** BIN 库导入行级错误的稳定代码（运营页面按代码展示错误明细）。 */
@Getter
@AllArgsConstructor
public enum BinImportErrorCode implements CodeEnum {
    /** 列数不是 7，或中间出现空行、引号不闭合。 */
    COLUMN_COUNT_MISMATCH("COLUMN_COUNT_MISMATCH"),

    /** 必填列为空。 */
    REQUIRED_MISSING("REQUIRED_MISSING"),

    /** 格式不符（位数、字符集、长度）。 */
    INVALID_FORMAT("INVALID_FORMAT"),

    /** 取值不在允许清单内。 */
    INVALID_VALUE("INVALID_VALUE"),

    /** 同一前缀在文件中出现多次（每一行都记错）。 */
    DUPLICATE_PREFIX("DUPLICATE_PREFIX"),

    /** 发卡国未在 Platform 国家区域映射中登记。 */
    COUNTRY_REGION_MISSING("COUNTRY_REGION_MISSING"),

    /** 该行不是合法 UTF-8 或含 NUL。 */
    ENCODING_INVALID("ENCODING_INVALID");

    /** 稳定代码 */
    private final String code;
}
