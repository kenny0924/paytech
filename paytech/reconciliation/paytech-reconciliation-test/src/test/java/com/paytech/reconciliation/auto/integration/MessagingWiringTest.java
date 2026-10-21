package com.paytech.reconciliation.auto.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;
import com.paytech.reconciliation.core.configuration.ReconciliationEventProperties;
import com.paytech.reconciliation.test.base.MockTestBase;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 消息装配（A 线测试底座自检）：reconciliation 发布端声明 reconciliation.events，经生产 DomainEventPublisher 发出的事件可由
 * ReconciliationEventProbe 的每例临时队列收到并按 reconciliation-api 载荷类反序列化；信封身份取 ReconciliationEventProperties
 * <li>四类事件的真实发布由 B / C 线的补偿 Handler 完成并在其入口测试中断言；本类只证明发布端拓扑、事件目录登记与观察队列可用。</li>
 *
 * @see DomainEventPublisher#publish(DomainEvent)
 */
class MessagingWiringTest extends MockTestBase {
    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private ReconciliationEventProperties eventProperties;

    /** ChannelBatchReconciled 信封经 Broker 确认后到达观察队列，载荷带 platformTxnCount（决定 I-02）。 */
    @Test
    void test_publishedEventReachesProbe() {
        long statementId = ReconciliationTestConstants.nextId();
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("statementId", String.valueOf(statementId));
        payload.put("currency", ReconciliationTestConstants.CURRENCY);
        payload.put("platformTxnCount", "2");
        payload.putArray("classifiedLines");
        DomainEvent event = DomainEvent.builder().eventType(ChannelBatchReconciledPayload.EVENT_TYPE)
            .schemaVersion(ChannelBatchReconciledPayload.SCHEMA_VERSION)
            .aggregate(ChannelBatchReconciledPayload.AGGREGATE_TYPE, String.valueOf(statementId))
            .aggregateVersion(ChannelBatchReconciledPayload.AGGREGATE_VERSION)
            .occurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS)).sourceService(eventProperties.getSourceService())
            .instanceId(eventProperties.getInstanceId()).payload(payload).build();

        publisher.publish(event);

        List<DomainEvent> received = events.await(1);
        ChannelBatchReconciledPayload body = events.payload(received.getFirst(), ChannelBatchReconciledPayload.class);
        assertEquals(event.eventId(), received.getFirst().eventId());
        assertEquals(statementId, body.getStatementId());
        assertEquals(2, body.getPlatformTxnCount());
        assertEquals(List.of(), body.getClassifiedLines());
    }
}
