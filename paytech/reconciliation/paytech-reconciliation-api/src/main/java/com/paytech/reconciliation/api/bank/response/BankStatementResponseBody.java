package com.paytech.reconciliation.api.bank.response;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 银行对账单头与导入进度。 */
@Getter
@Setter
public class BankStatementResponseBody {
    /** 银行对账单主键 */
    private String bankStatementId;

    /** 银行账户主键 */
    private String cashAccountId;

    /** 银行对账单编号 */
    private String statementRef;

    /** 期间开始日（含） */
    private LocalDate periodStart;

    /** 期间结束日（含） */
    private LocalDate periodEnd;

    /** 币种 */
    private String currency;

    /** 银行报告的期初余额（带符号），按币种精度补齐 */
    private String openingBalance;

    /** 银行报告的期末余额（带符号），按币种精度补齐 */
    private String closingBalance;

    /** 开始导入时声明的流水行数 */
    private Integer declaredLineCount;

    /** 已接收的流水行数 */
    private Integer importedLineCount;

    /** 因银行交易号已存在而跳过的行数 */
    private Integer duplicateLineCount;

    /** 原文件名 */
    private String fileName;

    /** 原文件 SHA-256 */
    private String fileContentHash;

    /**
     * 整单被拒的汇总原因
     * <li>只在 REJECTED 时非空。</li>
     */
    private String rejectReason;

    /** 状态：RECEIVING、IMPORTED、REJECTED */
    private String status;

    /** 上传人 Account ID */
    private String makerId;

    /** 开始导入的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
