package com.paytech.card.domain.binlibrary;

import com.paytech.card.api.enums.BinImportErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 导入文件的一条行级错误；说明与摘录都不含超过 8 位的连续数字。 */
@Getter
@AllArgsConstructor
public class BinImportRowError {
    /** 文件物理行号，表头为第 1 行。 */
    private final int lineNo;

    /** 出错列名；整行错误为 null。 */
    private final String columnName;

    /** 错误代码。 */
    private final BinImportErrorCode errorCode;

    /** 中文说明，不含出错值。 */
    private final String errorMessage;

    /** 出错值摘录；超过 8 位的连续数字为 null。 */
    private final String valueExcerpt;
}
