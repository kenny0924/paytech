package com.paytech.reconciliation.api.statement.response;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 跨账单查询的渠道账单行：行内容、匹配结果与所属账单、清算账户摘要。 */
@Getter
@Setter
public class ChannelStatementLineSearchItemResponseBody {
    /** 账单行主键 */
    private String lineId;

    /** 所属渠道账单主键 */
    private String statementId;

    /** 所属账单的渠道批次号 */
    private String channelBatchRef;

    /** 所属账单的版本（同一批次号重新导入时递增） */
    private Integer revision;

    /** 所属账单状态：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED */
    private String statementStatus;

    /** 所属账单的结算日 */
    private LocalDate settlementDate;

    /** 清算账户主键 */
    private String clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 币种（取自所属账单） */
    private String currency;

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

    /** 匹配到的平台流水主键；未匹配为空 */
    private String platformTxnId;

    /** 匹配方式：AUTO、MANUAL；未匹配为空 */
    private String matchMethod;

    /** 匹配的 UTC 时间；未匹配为空 */
    private Instant matchedTime;

    /** 确认的实际渠道费；FEE 行与未匹配行为空，按币种精度补齐 */
    private String actualFee;

    /** 已归类调整行指向的已应用处置主键；未归类为空 */
    private String resolutionId;

    /** 匹配状态：UNMATCHED、MATCHED、EXCEPTION */
    private String matchStatus;
}
