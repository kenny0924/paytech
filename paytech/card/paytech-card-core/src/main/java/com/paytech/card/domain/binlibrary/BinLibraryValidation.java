package com.paytech.card.domain.binlibrary;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 导入文件第一遍校验结果：文件级失败原因、行数、错误行数与前 200 条错误明细。 */
@Getter
@AllArgsConstructor
public class BinLibraryValidation {
    /** 实际字节的 SHA-256 小写十六进制；文件级失败提前结束读取时为 null。 */
    private final String contentHash;

    /** 文件级失败原因，不含卡号；没有文件级失败时为 null。 */
    private final String failureReason;

    /** 数据行数（不含表头与末尾空行）；文件级失败时为 null。 */
    private final Integer totalRows;

    /** 有错误的数据行数。 */
    private final int errorRows;

    /** 前 200 条错误明细，按发现先后。 */
    private final List<BinImportRowError> errors;

    /** 文件级失败的结果。 */
    static BinLibraryValidation fileFailure(String failureReason) {
        return new BinLibraryValidation(null, failureReason, null, 0, List.of());
    }

    /** 没有文件级失败且没有行错误。 */
    public boolean isValid() {
        return failureReason == null && errorRows == 0;
    }
}
