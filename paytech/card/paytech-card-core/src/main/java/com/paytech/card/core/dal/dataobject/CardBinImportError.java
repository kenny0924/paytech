package com.paytech.card.core.dal.dataobject;

import java.time.Instant;

import com.paytech.card.api.enums.BinImportErrorCode;

import lombok.Getter;
import lombok.Setter;

/** t_card_bin_import_error 的完整持久化映射：一条行级导入错误，不含卡号。 */
@Getter
@Setter
public class CardBinImportError {
    /** 错误明细标识。 */
    private Long errorId;

    /** 所属 BIN 库版本。 */
    private Long versionId;

    /** 文件物理行号，表头为第 1 行。 */
    private Integer lineNo;

    /** 出错列名；整行错误为 null。 */
    private String columnName;

    /** 错误代码。 */
    private BinImportErrorCode errorCode;

    /** 中文错误说明，不含卡号。 */
    private String errorMessage;

    /** 出错值摘录；超过 8 位的连续数字不回显（null）。 */
    private String valueExcerpt;

    /** 创建时间。 */
    private Instant createTime;

    /** 更新时间，等于创建时间。 */
    private Instant updateTime;
}
