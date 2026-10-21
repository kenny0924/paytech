package com.paytech.reconciliation.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * ChannelBatchReconciled Schema v1 Payload（事件目录 catalog/events.yaml，payloadSchemaRef
 * paytech-reconciliation-api/event/ChannelBatchReconciled/v1）：渠道账单通过对平门禁、转为 RECONCILED（或净额 NONE 直接 CASH_CONFIRMED）时
 * 发一次，billing 据此登记 CHANNEL_BATCH_RECONCILED
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = statementId，aggregateVersion 固定 {@value #AGGREGATE_VERSION}（每张账单版本至多对平一次），occurredAt = reconciledTime，
 * sourceService = reconciliation。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID、版本、行号与金额为十进制，日期为 YYYY-MM-DD，时间为 UTC RFC 3339），缺省值为 JSON null；
 * Jackson 默认把它们绑定到本类字段。金额按币种精度补齐。</li>
 * <li>净额关系：netAmount 带 cashDirection 等于 receivableTotal − payableTotal − actualFeeTotal + Σ classifiedLines.amount。</li>
 * <li>已归类调整行（渠道账单行的 CLASSIFY）不单独发事件，只在这里带出。消费方暂无（billing 需求 B 接入时登记）。</li>
 */
@Getter
@Setter
public class ChannelBatchReconciledPayload {
    /** 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "ChannelBatchReconciled";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：渠道账单。 */
    public static final String AGGREGATE_TYPE = "CHANNEL_STATEMENT";

    /** 聚合版本：每个聚合只发一次。 */
    public static final long AGGREGATE_VERSION = 1L;

    /** 渠道账单主键（t_channel_statement.id） */
    private Long statementId;

    /** 清算账户主键 */
    private Long clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 经营主体编号 */
    private String entityNo;

    /** 币种，大写 ISO 4217 */
    private String currency;

    /** 渠道结算批次号 */
    private String channelBatchRef;

    /** 同一批次号的导入版本 */
    private Integer revision;

    /** 渠道结算日 */
    private LocalDate settlementDate;

    /** 净额方向（CashDirection：CREDIT、DEBIT、NONE） */
    private String cashDirection;

    /** 净额绝对值 */
    private BigDecimal netAmount;

    /** 本账单覆盖的应收平台流水金额合计（PAYMENT 等贷记交易行） */
    private BigDecimal receivableTotal;

    /** 本账单覆盖的应付平台流水金额合计（REFUND、CHARGEBACK 等借记交易行） */
    private BigDecimal payableTotal;

    /** 本账单确认的实际渠道费合计 */
    private BigDecimal actualFeeTotal;

    /** 已归类调整行，按行号升序；没有为空列表 */
    private List<ClassifiedLine> classifiedLines;

    /**
     * 本账单使其变为 SETTLED 的平台流水笔数
     * <li>决定 I-02：不带主键列表（2 万行账单的列表会超过 64 KiB 信封）；需要明细时按账单 ID 查
     * {@code GET /reconciliation/v1/platform-txns?statementId=}。</li>
     */
    private Integer platformTxnCount;

    /**
     * 生成的预期银行流水主键
     * <li>净额方向为 NONE 时为空。</li>
     */
    private Long expectedMovementId;

    /** 对平的 UTC 微秒时间，即事件 occurredAt */
    private Instant reconciledTime;

    /** 已归类的渠道账单调整行。 */
    @Getter
    @Setter
    public static class ClassifiedLine {
        /** 文件内行号 */
        private Integer lineNo;

        /** 归类码（ReconClassification） */
        private String classificationCode;

        /** 归类金额，带符号（对平台为流入为正） */
        private BigDecimal amount;

        /** 已应用的处置主键 */
        private Long resolutionId;
    }
}
