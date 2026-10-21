package com.paytech.reconciliation.service.processor.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.paytech.reconciliation.api.enums.CashDirection;

import lombok.Builder;
import lombok.Getter;

/** 开始导入渠道账单命令：账单头与文件信息（technique D-01）。 */
@Getter
@Builder
public class StartChannelStatementImportRequest {
    /** 上传人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 清算账户编码 */
    @NotBlank
    private final String clearingAccountCode;

    /** 渠道结算批次号 */
    @NotBlank
    private final String channelBatchRef;

    /** 结算日 */
    @NotNull
    private final LocalDate settlementDate;

    /** 账单币种 */
    @NotBlank
    private final String currency;

    /** 账单头总额（带符号） */
    @NotNull
    private final BigDecimal grossAmount;

    /** 账单头费用合计 */
    @NotNull
    private final BigDecimal feeAmount;

    /** 净额绝对值 */
    @NotNull
    private final BigDecimal netAmount;

    /** 净额方向 */
    @NotNull
    private final CashDirection cashDirection;

    /** 原文件名 */
    @NotBlank
    private final String fileName;

    /** 原文件 SHA-256 */
    @NotBlank
    private final String fileContentHash;

    /** 声明行数 1–20,000 */
    @NotNull
    @Min(1)
    @Max(20000)
    private final Integer declaredLineCount;
}
