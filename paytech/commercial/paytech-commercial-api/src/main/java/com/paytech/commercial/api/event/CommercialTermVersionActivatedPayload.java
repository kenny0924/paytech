package com.paytech.commercial.api.event;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 商业条款版本生效事件载荷（technique.md §2.8）
 * <li>事件信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = planId，aggregateVersion = changeVersion，occurredAt = occurredAt，sourceService = commercial。</li>
 * <li>只带身份与版本；价格内容由消费方按 versionId 回读 commercial。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID 与版本为十进制字符串，时间为 UTC RFC 3339），缺省为 JSON null，符合 Foundation JSON Object
 * Profile；Jackson 默认把它们绑定到本类的 Long / Instant 字段。</li>
 */
@Getter
@Setter
public class CommercialTermVersionActivatedPayload {
    /** 事件类型，与事件目录 catalog/events.yaml 一致。 */
    public static final String EVENT_TYPE = "CommercialTermVersionActivated";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：商业条款方案。 */
    public static final String AGGREGATE_TYPE = "COMMERCIAL_TERM_PLAN";

    /** 方案 ID（t_commercial_term_plan.term_plan_id）。 */
    private Long planId;

    /** 条款类型（目录登记值）。 */
    private String termType;

    /** 方案所属合同 ID；无合同的方案为空。 */
    private Long agreementId;

    /** 新生效版本 ID。 */
    private Long versionId;

    /** 新生效版本号。 */
    private Long versionNo;

    /** 新生效版本的生效起点（含）。 */
    private Instant effectiveFrom;

    /** 被本次生效替代的前一 ACTIVE 版本 ID；方案首次生效为空。 */
    private Long supersededVersionId;

    /** COMMERCIAL_TERM_PLAN 根修订版本（BCR Root current_version），即事件 aggregateVersion；消费方只接受更高版本。 */
    private Long changeVersion;

    /** 版本生效提交的 UTC 微秒时间。 */
    private Instant occurredAt;
}
