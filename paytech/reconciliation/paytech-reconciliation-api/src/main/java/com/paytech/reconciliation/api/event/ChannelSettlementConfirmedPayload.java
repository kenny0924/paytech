package com.paytech.reconciliation.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * ChannelSettlementConfirmed Schema v1 Payload（事件目录 catalog/events.yaml，payloadSchemaRef
 * paytech-reconciliation-api/event/ChannelSettlementConfirmed/v1）：渠道（CHANNEL_SETTLEMENT）预期银行流水的一条银行匹配确认时发一次，
 * billing 据此登记 CHANNEL_SETTLEMENT
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = bankMatchId，aggregateVersion 固定 {@value #AGGREGATE_VERSION}（按匹配 ID 幂等，每条确认匹配只发一次），
 * occurredAt = confirmedTime，sourceService = reconciliation。</li>
 * <li>事件 JSON 中所有标量都是字符串，缺省值为 JSON null；Jackson 默认把它们绑定到本类字段。金额按币种精度补齐。</li>
 * <li>消费方暂无（billing 需求 B 接入时登记）。</li>
 */
@Getter
@Setter
public class ChannelSettlementConfirmedPayload {
    /** 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "ChannelSettlementConfirmed";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：银行匹配。 */
    public static final String AGGREGATE_TYPE = "BANK_MATCH";

    /** 聚合版本：每个聚合只发一次。 */
    public static final long AGGREGATE_VERSION = 1L;

    /** 银行匹配主键（t_bank_match.id） */
    private Long bankMatchId;

    /** 预期银行流水主键 */
    private Long expectedMovementId;

    /** 渠道账单主键（预期银行流水的来源） */
    private Long statementId;

    /** 清算账户主键 */
    private Long clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 银行账户主键 */
    private Long cashAccountId;

    /** 经营主体编号 */
    private String entityNo;

    /** 币种，大写 ISO 4217 */
    private String currency;

    /** 资金方向（SettlementDirection：CREDIT 打入平台、DEBIT 从平台扣出），取预期银行流水方向 */
    private String direction;

    /** 本次确认的金额，正数 */
    private BigDecimal matchedAmount;

    /** 银行流水主键 */
    private Long bankStatementLineId;

    /** 银行流水起息日 */
    private LocalDate valueDate;

    /** 确认的 UTC 微秒时间，即事件 occurredAt */
    private Instant confirmedTime;
}
