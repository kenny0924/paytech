package com.paytech.reconciliation.api.event;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * ActualChannelCostConfirmed Schema v1 Payload（事件目录 catalog/events.yaml，payloadSchemaRef
 * paytech-reconciliation-api/event/ActualChannelCostConfirmed/v1）：渠道账单对平时，本账单使其变为 SETTLED 的每笔平台流水，
 * 若实际渠道费 ≠ billing 预估则发一条（technique D-05）
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = platformTxnId，aggregateVersion 固定 {@value #AGGREGATE_VERSION}（每笔平台流水至多发一次），occurredAt = confirmedTime，
 * sourceService = reconciliation。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID、金额为十进制，时间为 UTC RFC 3339），缺省值为 JSON null，符合 Foundation JSON Object Profile；
 * Jackson 默认把它们绑定到本类的 Long / BigDecimal / Instant 字段（同 billing {@code BillingFactPayload}）。金额按币种精度补齐。</li>
 * <li>消费方暂无（billing 需求 B 接入 COST_ADJUSTMENT 时登记）。</li>
 */
@Getter
@Setter
public class ActualChannelCostConfirmedPayload {
    /** 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "ActualChannelCostConfirmed";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：平台流水。 */
    public static final String AGGREGATE_TYPE = "PLATFORM_TXN";

    /** 聚合版本：每个聚合只发一次。 */
    public static final long AGGREGATE_VERSION = 1L;

    /** 平台流水主键（t_platform_txn.id） */
    private Long platformTxnId;

    /** 平台流水来源的 billing 事实主键 */
    private Long billingFactId;

    /** 清算账户主键 */
    private Long clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 经营主体编号 */
    private String entityNo;

    /** 币种，大写 ISO 4217 */
    private String currency;

    /** 平台流水来源 billing 事实类型（PlatformTxnSource） */
    private String txnSource;

    /** billing 预估的渠道费（平台流水 expected_fee） */
    private BigDecimal expectedFee;

    /** 账单行确认的实际渠道费累计（平台流水 actual_fee） */
    private BigDecimal actualFee;

    /** 差额：actualFee − expectedFee，带符号，不为 0 */
    private BigDecimal difference;

    /** Payment 渠道交易引用内部 ID */
    private String channelTransactionRefId;

    /** 渠道外部交易号类型 */
    private String referenceType;

    /** 渠道外部交易号 */
    private String externalTransactionId;

    /** 使平台流水变为 SETTLED 的渠道账单主键 */
    private Long statementId;

    /** 确认时间：所在账单对平的 UTC 微秒时间，即事件 occurredAt */
    private Instant confirmedTime;
}
