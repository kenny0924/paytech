package com.paytech.compliance.ext.mq;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.paytech.compliance.api.event.QualificationChangedPayload;
import com.paytech.compliance.domain.QualificationChangedFact;
import com.paytech.compliance.integration.QualificationChangePublisher;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import tools.jackson.databind.json.JsonMapper;

/**
 * 角色资格状态迁移的领域事件发布（QualificationChanged Schema v1）
 * <li>aggregateType=ROLE_QUALIFICATION、aggregateId=qualificationId、aggregateVersion=qualificationVersion；Payload 字段顺序即
 * {@link QualificationChangedPayload#FIELDS}。</li>
 * <li>收到 Broker 确认且路由到订阅队列才返回；否则抛出，由补偿任务重试。</li>
 */
@Component
public class QualificationChangedEventPublisher implements QualificationChangePublisher {
    private final JsonMapper jsonMapper;

    private final DomainEventPublisher domainEvents;

    /** 信封 sourceService。 */
    private final String sourceService;

    /** 信封 instanceId。 */
    private final String instanceId;

    /** 注入 JSON、发布端与信封身份。 */
    public QualificationChangedEventPublisher(JsonMapper jsonMapper, DomainEventPublisher domainEvents,
        @Value("${messaging.producer.service}") String sourceService, @Value("${paytech.instance-id}") String instanceId) {
        this.jsonMapper = jsonMapper;
        this.domainEvents = domainEvents;
        this.sourceService = sourceService;
        this.instanceId = instanceId;
    }

    /** 发布资格迁移。 */
    @Override
    public void publish(QualificationChangedFact fact) {
        domainEvents.publish(envelope(fact));
    }

    /** QualificationChanged v1 信封。 */
    DomainEvent envelope(QualificationChangedFact fact) {
        return DomainEvent.builder().eventType(QualificationChangedPayload.EVENT_TYPE)
            .schemaVersion(QualificationChangedPayload.SCHEMA_VERSION)
            .aggregate(QualificationChangedPayload.AGGREGATE_TYPE, Long.toString(fact.getQualificationId()))
            .aggregateVersion(fact.getQualificationVersion()).occurredAt(fact.getChangedAt()).sourceService(sourceService)
            .instanceId(instanceId).payload(jsonMapper.valueToTree(payload(fact))).build();
    }

    /** Payload，字段顺序即 {@link QualificationChangedPayload#FIELDS}。 */
    static Map<String, Object> payload(QualificationChangedFact fact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualificationId", Long.toString(fact.getQualificationId()));
        payload.put("qualificationVersion", fact.getQualificationVersion());
        payload.put("roleType", fact.getRoleType());
        payload.put("roleId", fact.getRoleId());
        payload.put("qualificationType", fact.getQualificationType());
        payload.put("previousStatus", fact.getPreviousStatus());
        payload.put("status", fact.getStatus());
        payload.put("decisionId", Long.toString(fact.getDecisionId()));
        payload.put("decisionSource", fact.getDecisionSource());
        payload.put("caseId", fact.getCaseId() == null ? null : Long.toString(fact.getCaseId()));
        payload.put("validFrom", fact.getValidFrom().toString());
        payload.put("validTo", fact.getValidTo() == null ? null : fact.getValidTo().toString());
        payload.put("tier", fact.getTier());
        payload.put("riskRating", fact.getRiskRating());
        payload.put("jurisdictionCode", fact.getJurisdictionCode());
        payload.put("responsiblePartnerId", fact.getResponsiblePartnerId() == null ? null : Long.toString(fact.getResponsiblePartnerId()));
        payload.put("reasonCode", fact.getReasonCode());
        payload.put("changedAt", fact.getChangedAt().toString());
        return payload;
    }
}
