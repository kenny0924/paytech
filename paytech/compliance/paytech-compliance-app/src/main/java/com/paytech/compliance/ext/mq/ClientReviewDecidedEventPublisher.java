package com.paytech.compliance.ext.mq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.paytech.compliance.api.event.ClientReviewDecidedPayload;
import com.paytech.compliance.domain.ClientReviewResult;
import com.paytech.compliance.integration.ClientReviewResultPublisher;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

import tools.jackson.databind.json.JsonMapper;

/**
 * 审核结果的领域事件发布（ClientReviewDecided Schema v2）
 * <li>经 {@link DomainEventPublisher} 发布到 compliance.events（aggregateType=DD_CASE、aggregateId=caseId、aggregateVersion=caseVersion），
 * Payload 字段顺序即 {@link ClientReviewDecidedPayload#FIELDS}；v2 在 v1 字段后追加 caseType、roundNo、tier、riskRating、dueTime、errors。</li>
 * <li>收到 Broker 确认且路由到订阅队列才返回；未确认、未路由（FAIL 策略）或超时抛出，由补偿任务重试，消费方按 eventId 防重。</li>
 */
@Component
public class ClientReviewDecidedEventPublisher implements ClientReviewResultPublisher {
    private final JsonMapper jsonMapper;

    private final DomainEventPublisher domainEvents;

    /** 信封 sourceService，与 messaging.producer.service 一致。 */
    private final String sourceService;

    /** 信封 instanceId（实例清单 instance.id）。 */
    private final String instanceId;

    /** 注入 JSON、发布端与信封身份。 */
    public ClientReviewDecidedEventPublisher(JsonMapper jsonMapper, DomainEventPublisher domainEvents,
        @Value("${messaging.producer.service}") String sourceService, @Value("${paytech.instance-id}") String instanceId) {
        this.jsonMapper = jsonMapper;
        this.domainEvents = domainEvents;
        this.sourceService = sourceService;
        this.instanceId = instanceId;
    }

    /** 发布审核结果。 */
    @Override
    public void publish(ClientReviewResult result) {
        domainEvents.publish(envelope(result));
    }

    /** ClientReviewDecided v2 信封；eventId 默认取 AggregateKey 四段式事件键，补偿重试不变。 */
    DomainEvent envelope(ClientReviewResult result) {
        return DomainEvent.builder().eventType(ClientReviewDecidedPayload.EVENT_TYPE)
            .schemaVersion(ClientReviewDecidedPayload.SCHEMA_VERSION)
            .aggregate(ClientReviewDecidedPayload.AGGREGATE_TYPE, Long.toString(result.getCaseId()))
            .aggregateVersion(result.getCaseVersion()).occurredAt(result.getDecidedAt()).sourceService(sourceService)
            .instanceId(instanceId).payload(jsonMapper.valueToTree(payload(result))).build();
    }

    /** Payload，字段顺序即 {@link ClientReviewDecidedPayload#FIELDS}。 */
    static Map<String, Object> payload(ClientReviewResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", Long.toString(result.getCaseId()));
        payload.put("caseVersion", result.getCaseVersion());
        payload.put("applicationId", Long.toString(result.getApplicationId()));
        payload.put("submissionId", result.getSourceSubmissionId());
        payload.put("outcome", result.getOutcome());
        payload.put("returnSection", result.getReturnSection());
        payload.put("publicNote", result.getPublicNote());
        payload.put("decidedAt", result.getDecidedAt().toString());
        payload.put("decisionId", result.getDecisionId() == null ? null : Long.toString(result.getDecisionId()));
        payload.put("decidedBy", result.getDecidedBy() == null ? null : Long.toString(result.getDecidedBy()));
        payload.put("caseType", result.getCaseType());
        payload.put("roundNo", result.getRoundNo());
        payload.put("tier", result.getTier());
        payload.put("riskRating", result.getRiskRating());
        payload.put("dueTime", result.getDueTime() == null ? null : result.getDueTime().toString());
        payload.put("errors", errors(result));
        return payload;
    }

    /** 退回需求级错误，字段同 ClientReviewDecidedErrorPayload。 */
    static List<Map<String, Object>> errors(ClientReviewResult result) {
        return result.getErrors().stream().map(error -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("requirementKey", error.getRequirementKey());
            item.put("subjectPartyId", error.getSubjectPartyId() == null ? null : Long.toString(error.getSubjectPartyId()));
            item.put("code", error.getCode());
            item.put("reason", error.getReason());
            item.put("remediation", error.getRemediation());
            return item;
        }).toList();
    }
}
