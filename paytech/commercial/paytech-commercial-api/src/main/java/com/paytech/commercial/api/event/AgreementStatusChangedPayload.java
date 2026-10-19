package com.paytech.commercial.api.event;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同状态变更事件载荷（technique.md §2.8）
 * <li>事件信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = agreementId，aggregateVersion = changeVersion，occurredAt = occurredAt，sourceService = commercial。</li>
 * <li>只带身份、状态与生效区间；消费方需要合同明细时按 agreementId 回读 commercial。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID 与版本为十进制字符串，时间为 UTC RFC 3339），缺省为 JSON null，符合 Foundation JSON Object
 * Profile；Jackson 默认把它们绑定到本类的 Long / Instant 字段。</li>
 */
@Getter
@Setter
public class AgreementStatusChangedPayload {
    /** 事件类型，与事件目录 catalog/events.yaml 一致。 */
    public static final String EVENT_TYPE = "AgreementStatusChanged";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：合同。 */
    public static final String AGGREGATE_TYPE = "AGREEMENT";

    /** 合同 ID（t_agreement.agreement_id）。 */
    private Long agreementId;

    /** 合同编号，全局唯一。 */
    private String agreementNo;

    /** 合同类型代码（CLIENT、PARTNER）。 */
    private String agreementType;

    /** CLIENT 合同的 Client ID；PARTNER 合同为空。 */
    private Long clientId;

    /** PARTNER 合同的 Local Partner ID；CLIENT 合同为空。 */
    private Long localPartnerId;

    /** 合同主体 Operating Entity 编号；PARTNER 合同的覆盖 OE 见合同明细，可为空。 */
    private String entityNo;

    /** 变更后的合同状态代码（ACTIVE、TERMINATED、EXPIRED 等）。 */
    private String status;

    /** 生效起点（含）。 */
    private Instant effectiveFrom;

    /** 生效终点（不含）；未终止为空。 */
    private Instant effectiveTo;

    /** AGREEMENT 根修订版本（BCR Root current_version），即事件 aggregateVersion；消费方只接受更高版本。 */
    private Long changeVersion;

    /** 状态变更提交的 UTC 微秒时间。 */
    private Instant occurredAt;
}
