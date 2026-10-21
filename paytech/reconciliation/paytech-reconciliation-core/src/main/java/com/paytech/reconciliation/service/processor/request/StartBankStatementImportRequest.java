package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

/** 开始导入银行对账单命令：所选银行账户、对账单头与文件信息（technique D-01、§2.5）。 */
@Getter
@Builder
public class StartBankStatementImportRequest {
    /** 上传人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 银行账户主键 */
    @NotNull
    private final Long cashAccountId;

    /** 文件头的银行账号末四位 */
    @NotBlank
    private final String bankAccountLast4;

    /** 银行对账单编号 */
    @NotBlank
    private final String statementRef;

    /** 期间开始日（含） */
    @NotNull
    private final LocalDate periodStart;

    /** 期间结束日（含） */
    @NotNull
    private final LocalDate periodEnd;

    /** 币种 */
    @NotBlank
    private final String currency;

    /** 期初余额（带符号） */
    @NotNull
    private final BigDecimal openingBalance;

    /** 期末余额（带符号） */
    @NotNull
    private final BigDecimal closingBalance;

    /** 原文件名 */
    @NotBlank
    private final String fileName;

    /** 原文件 SHA-256 */
    @NotBlank
    private final String fileContentHash;

    /** 声明行数 0–20,000 */
    @NotNull
    @Min(0)
    @Max(20000)
    private final Integer declaredLineCount;
}
