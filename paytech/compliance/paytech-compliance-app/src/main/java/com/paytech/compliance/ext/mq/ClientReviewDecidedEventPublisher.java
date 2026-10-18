package com.paytech.compliance.ext.mq;

import java.util.LinkedHashMap;
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
 * 审核结果的领域事件发布（design/07 §5、design/08 §3）
 * <li>经 {@link DomainEventPublisher} 发布 ClientReviewDecided Schema v1 到 compliance.events（aggregateType=DD_CASE、
 * aggregateId=caseId、aggregateVersion=caseVersion），Payload 字段顺序即 {@link ClientReviewDecidedPayload#FIELDS}。</li>
 * <li>client-master 已切到 client-master.compliance.p0..p3，旧 topic_risk_client_review 第 08 步停发。</li>
 * <li>收到 Broker 确认且路由到订阅队列才返回；未确认、未路由（FAIL 策略）或超时抛出，由补偿任务重试，消费方按 eventId 防重。</li>
 */
@Component
public class ClientReviewDecidedEventPublisher implements ClientReviewResultPublisher {
    /** 事件类型。 */
    public static final String EVENT_TYPE = ClientReviewDecidedPayload.EVENT_TYPE;

    private final JsonMapper jsonMapper;

    private final DomainEventPublisher domainEvents;

    /** 信封 sourceService，与 messaging.producer.service 一致。 */
    private final String sourceService;

    /** 信封 instanceId（实例清单 instance.id）。 */
    private final String instanceId;

    public ClientReviewDecidedEventPublisher(JsonMapper jsonMapper, DomainEventPublisher domainEvents,
        @Value("${messaging.producer.service}") String sourceService, @Value("${paytech.instance-id}") String instanceId) {
        this.jsonMapper = jsonMapper;
        this.domainEvents = domainEvents;
        this.sourceService = sourceService;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(ClientReviewResult result) {
        domainEvents.publish(envelope(result));
    }

    /** ClientReviewDecided Schema v1 信封；eventId 默认取 AggregateKey 四段式事件键，补偿重试不变。 */
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
        return payload;
    }
}
