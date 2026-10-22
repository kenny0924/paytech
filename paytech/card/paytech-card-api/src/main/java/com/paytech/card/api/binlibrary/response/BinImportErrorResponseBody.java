package com.paytech.card.api.binlibrary.response;

import lombok.Getter;
import lombok.Setter;

/** BIN 库导入的一条行级错误 */
@Getter
@Setter
public class BinImportErrorResponseBody {
    /** 文件物理行号，表头为第 1 行 */
    private int lineNo;

    /** 出错列名；整行错误（列数不符、重复前缀等）为 null */
    private String columnName;

    /**
     * 错误代码，{@code BinImportErrorCode} Code：COLUMN_COUNT_MISMATCH、REQUIRED_MISSING、INVALID_FORMAT、INVALID_VALUE、
     * DUPLICATE_PREFIX、COUNTRY_REGION_MISSING、ENCODING_INVALID
     */
    private String errorCode;

    /** 面向运营的中文说明，不含卡号 */
    private String errorMessage;

    /** 出错值摘录：超过 8 位的连续数字不回显（为 null），其他值截断到 64 字符 */
    private String valueExcerpt;
}
