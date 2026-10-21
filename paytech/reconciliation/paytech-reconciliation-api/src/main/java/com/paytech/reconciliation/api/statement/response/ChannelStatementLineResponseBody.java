package com.paytech.reconciliation.api.statement.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 渠道账单行：文件内容与匹配、实际费用、归类结果。 */
@Getter
@Setter
public class ChannelStatementLineResponseBody {
    /** 账单行主键 */
    private String lineId;

    /** 渠道账单主键 */
    private String statementId;

    /** 文件内行号 */
    private Integer lineNo;

    /** 行类型：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT */
    private String lineType;

    /** 相对平台的方向：CREDIT、DEBIT */
    private String direction;

    /** 行金额，按币种精度补齐 */
    private String amount;

    /** 行上直接扣的渠道费，按币种精度补齐 */
    private String feeAmount;

    /** 外部交易号类型；ADJUSTMENT 行与不带交易号的 FEE 行为空 */
    private String referenceType;

    /** 渠道外部交易号；为空规则同 referenceType */
    private String externalTransactionId;

    /** 渠道记录的交易日期；可为空 */
    private LocalDate transactionDate;

    /** 标准列以外的渠道特有列；可为空 */
    private Map<String, String> rawAttributes;

    /**
     * 匹配到的平台流水主键
     * <li>未匹配为空。</li>
     */
    private String platformTxnId;

    /** 匹配方式：AUTO、MANUAL；未匹配为空 */
    private String matchMethod;

    /** 匹配的 UTC 时间；未匹配为空 */
    private Instant matchedTime;

    /**
     * 确认的实际渠道费（本行 feeAmount 加同交易号 FEE 行）
     * <li>FEE 行与未匹配行为空；按币种精度补齐。</li>
     */
    private String actualFee;

    /**
     * 已归类调整行指向的已应用处置主键
     * <li>未归类为空。</li>
     */
    private String resolutionId;

    /** 匹配状态：UNMATCHED、MATCHED、EXCEPTION */
    private String matchStatus;
}
