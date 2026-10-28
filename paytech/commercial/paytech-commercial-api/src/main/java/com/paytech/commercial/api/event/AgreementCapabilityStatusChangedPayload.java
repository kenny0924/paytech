package com.paytech.commercial.api.event;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同开通项执行状态变更事件载荷（开通项前置门禁 technique.md §11.1）
 * <li>事件信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = agreementId，aggregateVersion = changeVersion（合同 BCR Root 版本，跳号），sourceService = commercial。</li>
 * <li>一个合同版本一条事件，changes 列出本次全部执行状态变化（门禁一次核对可能迁移多个开通项）。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID 与版本为十进制字符串，时间为 UTC RFC 3339），缺省为 JSON null。</li>
 */
@Getter
@Setter
public class AgreementCapabilityStatusChangedPayload {
    /** 事件类型，与事件目录 catalog/events.yaml 一致。 */
    public static final String EVENT_TYPE = "AgreementCapabilityStatusChanged";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：合同。 */
    public static final String AGGREGATE_TYPE = "AGREEMENT";

    /** 合同 ID（t_agreement.agreement_id）。 */
    private Long agreementId;

    /** CLIENT 合同的 Client ID；PARTNER 合同为空。 */
    private Long clientId;

    /** AGREEMENT 根修订版本，即事件 aggregateVersion；消费方只接受更高版本。 */
    private Long changeVersion;

    /** 本次变更提交的 UTC 微秒时间。 */
    private Instant occurredAt;

    /** 本次执行状态变化，按开通项 ID 升序，至少一条。 */
    private List<Change> changes;

    /** 一个开通项的执行状态变化。 */
    @Getter
    @Setter
    public static class Change {
        /** 开通项 ID */
        private Long agreementCapabilityId;

        /** 准确 OE */
        private String entityNo;

        /** 可开通项代码 */
        private String capabilityCode;

        /** 可开通项种类代码 */
        private String capabilityKind;

        /** 变更前执行状态；新增开通项或此前为 DISABLED 时为空 */
        private String fromExecutionStatus;

        /** 变更后执行状态；关闭（DISABLED）时为空 */
        private String toExecutionStatus;

        /** 触发来源：PREREQUISITE（门禁）、MANUAL（运营暂停 / 恢复）、MEMBERSHIP（新增、关闭或重新纳入） */
        private String trigger;
    }
}
