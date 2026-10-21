package com.paytech.reconciliation.api.event;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * ReconAdjustmentApproved Schema v1 Payload（事件目录 catalog/events.yaml，payloadSchemaRef
 * paytech-reconciliation-api/event/ReconAdjustmentApproved/v1）：来源对象为平台流水、预期银行流水或银行流水的 CLASSIFY 处置批准时发一次，
 * billing 据此登记 RECON_ADJUSTMENT；渠道账单行的归类不发本事件
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = resolutionId，aggregateVersion 固定 {@value #AGGREGATE_VERSION}（处置批准即应用，只发一次），occurredAt = approvedTime，
 * sourceService = reconciliation。</li>
 * <li>事件 JSON 中所有标量都是字符串，缺省值为 JSON null；Jackson 默认把它们绑定到本类字段。金额按币种精度补齐。</li>
 * <li>消费方暂无（billing 需求 B 接入时登记）。</li>
 */
@Getter
@Setter
public class ReconAdjustmentApprovedPayload {
    /** 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "ReconAdjustmentApproved";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：异常处置。 */
    public static final String AGGREGATE_TYPE = "RECON_RESOLUTION";

    /** 聚合版本：每个聚合只发一次。 */
    public static final long AGGREGATE_VERSION = 1L;

    /** 处置主键（t_recon_resolution.id） */
    private Long resolutionId;

    /** 异常主键 */
    private Long exceptionId;

    /** 来源对象类型（ReconExceptionSourceType：PLATFORM_TXN、EXPECTED_BANK_MOVEMENT、BANK_STATEMENT_LINE） */
    private String sourceType;

    /** 来源对象主键 */
    private Long sourceId;

    /** 归类码（ReconClassification） */
    private String classificationCode;

    /** 主体类型（ReconEntityType）：平台流水、预期银行流水为 OPERATING_ENTITY；银行流水取银行账户主体 */
    private String entityType;

    /** 主体标识：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串 */
    private String entityId;

    /** 币种，大写 ISO 4217 */
    private String currency;

    /** 归类金额，带符号，等于异常差异金额 */
    private BigDecimal amount;

    /** 清算账户主键；来源为平台流水时非空，其余为空 */
    private Long clearingAccountId;

    /** 银行账户主键；来源为预期银行流水或银行流水时非空，其余为空 */
    private Long cashAccountId;

    /** 提交人 Account ID */
    private Long makerId;

    /** 复核人 Account ID */
    private Long checkerId;

    /** 复核人确认的处置内容摘要（SHA-256 小写十六进制） */
    private String approvedContentHash;

    /** 批准并应用的 UTC 微秒时间，即事件 occurredAt */
    private Instant approvedTime;
}
