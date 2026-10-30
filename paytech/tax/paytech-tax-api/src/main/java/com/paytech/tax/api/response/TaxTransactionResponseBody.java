package com.paytech.tax.api.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一条正式税务交易及其底稿与单据 */
@Getter
@Setter
public class TaxTransactionResponseBody {
    /** 交易 ID */
    private String transactionId;

    /** 冻结计算 ID */
    private String calculationId;

    /** 来源系统 */
    private String sourceSystem;

    /** 来源事实类型，例如 ORDER_PAYMENT_SUCCEEDED */
    private String sourceFactType;

    /** 来源事实 ID（订单 ID） */
    private String sourceFactId;

    /** 税务范围键 */
    private String taxScopeKey;

    /** 来源方式：本期 AUTO */
    private String origin;

    /** 交易类型：本期 ORIGINAL */
    private String transactionKind;

    /** 纳税主体 Party ID */
    private String taxSubjectPartyId;

    /** 币种 */
    private String currency;

    /** 经济事件发生时间（支付成功时间） */
    private Instant occurredAt;

    /** 法定税点日期 yyyy-MM-dd */
    private String taxPointDate;

    /** 税点依据 */
    private String taxPointBasis;

    /** 本系统登记时间 */
    private Instant registeredAt;

    /** 底稿状态（TaxReportingStatus） */
    private String reportingStatus;

    /** 底稿未齐原因；READY 为空。本期为 FX_RATE_MISSING */
    private String reportingBlockReason;

    /** 申报底稿 */
    private List<TaxReportingEntryResponseBody> reportingEntries;

    /** 单据 */
    private List<TaxDocumentResponseBody> documents;
}
