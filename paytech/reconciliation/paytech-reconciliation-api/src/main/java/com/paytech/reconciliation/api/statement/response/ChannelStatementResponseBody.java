package com.paytech.reconciliation.api.statement.response;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 渠道账单头与导入、对平进度。 */
@Getter
@Setter
public class ChannelStatementResponseBody {
    /** 渠道账单主键 */
    private String statementId;

    /** 清算账户主键 */
    private String clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 渠道结算批次号 */
    private String channelBatchRef;

    /** 同一批次号的导入版本，从 1 起 */
    private Integer revision;

    /** 渠道结算日 */
    private LocalDate settlementDate;

    /** 账单币种 */
    private String currency;

    /**
     * 账单头总额
     * <li>带符号：贷记减借记，不含 FEE 行；按币种精度补齐。</li>
     */
    private String grossAmount;

    /** 账单头费用合计，按币种精度补齐 */
    private String feeAmount;

    /** 净额绝对值，方向见 cashDirection，按币种精度补齐 */
    private String netAmount;

    /** 净额方向：CREDIT、DEBIT、NONE */
    private String cashDirection;

    /** 开始导入时声明的明细行数 */
    private Integer declaredLineCount;

    /** 已接收的明细行数 */
    private Integer importedLineCount;

    /** 原文件名 */
    private String fileName;

    /** 原文件 SHA-256 */
    private String fileContentHash;

    /**
     * 整单被拒的汇总原因
     * <li>只在 REJECTED 时非空。</li>
     */
    private String rejectReason;

    /** 状态：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED */
    private String status;

    /** 净额中已经银行确认或归类结清的金额，按币种精度补齐 */
    private String cashConfirmedAmount;

    /**
     * 对平的 UTC 时间
     * <li>未对平为空。</li>
     */
    private Instant reconciledTime;

    /** 上传人 Account ID */
    private String makerId;

    /** 乐观锁版本 */
    private String version;

    /** 开始导入的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
