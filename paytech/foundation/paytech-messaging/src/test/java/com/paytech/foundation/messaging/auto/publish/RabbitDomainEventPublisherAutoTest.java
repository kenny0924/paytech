package com.paytech.foundation.messaging.auto.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.codec.PayloadHasher;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;
import com.paytech.foundation.messaging.test.base.MessagingMockTestBase;
import com.paytech.foundation.messaging.test.support.TestRun;

import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ 发布：AMQP 属性、Publisher Confirm、退回与信封大小上限。
 *
 * @see RabbitDomainEventPublisher#publish(DomainEvent)
 */
class RabbitDomainEventPublisherAutoTest extends MessagingMockTestBase {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private MessagingTopologyDeclarer declarer;

    @Autowired
    private MessagingMetrics metrics;

    @Test
    void test_normalFlow_envelopeAndAmqpProperties() {
        String observer = TestRun.PREFIX + ".observer." + UUID.randomUUID();
        broker.declareObserver(observer, TestRun.PRODUCER);
        try {
            DomainEvent event = DomainEvent.builder().eventType(TestRun.UNSUBSCRIBED_EVENT)
                .aggregate("TEST_AUDIT", "880001").aggregateVersion(7)
                .occurredAt(java.time.Instant.parse("2026-10-17T10:00:00.123456Z")).sourceService(TestRun.PRODUCER)
                .instanceId("main").correlationId("req-9001")
                .payload(JsonMapper.builder().build().createObjectNode().put("auditId", "880001")).build();

            publisher.publish(event);

            Message message = broker.receive(observer);
            assertNotNull(message);
            assertEquals(event.eventId(), message.getMessageProperties().getMessageId());
            assertEquals(TestRun.UNSUBSCRIBED_EVENT, message.getMessageProperties().getType());
            assertEquals("req-9001", message.getMessageProperties().getCorrelationId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getReceivedDeliveryMode());
            assertEquals("880001", message.getMessageProperties().getHeader(MessagingTopology.AGGREGATE_ID_HEADER));
            assertEquals("880001", message.getMessageProperties().getReceivedRoutingKey());
            assertEquals(MessagingTopology.producerExchange(TestRun.PRODUCER),
                message.getMessageProperties().getReceivedExchange());
            DomainEvent received = new DomainEventCodec().decode(message.getBody());
            assertEquals(PayloadHasher.hash(event.payload()), received.payloadHash());
            assertEquals(event.eventId(), received.eventId());
            assertEquals(event.payload(), received.payload());
            assertEquals(1.0, published(TestRun.UNSUBSCRIBED_EVENT));
        } finally {
            broker.deleteQueue(observer);
        }
    }

    @Test
    void test_businessRuleViolation_unroutedEventThrows() {
        declarer.declareProducer(TestRun.ORPHAN_PRODUCER);
        RabbitDomainEventPublisher orphan = publisherFor(TestRun.ORPHAN_PRODUCER);

        DomainEventPublishException exception = assertThrows(DomainEventPublishException.class,
            () -> orphan.publish(orphanEvent(TestRun.ORPHAN_PRODUCER)));

        assertTrue(exception.getMessage().contains("未路由"), exception.getMessage());
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
    }

    @Test
    void test_businessRuleViolation_brokerRejectionThrows() {
        RabbitDomainEventPublisher missing = publisherFor(TestRun.MISSING_PRODUCER);

        assertThrows(DomainEventPublishException.class, () -> missing.publish(orphanEvent(TestRun.MISSING_PRODUCER)));
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
    }

    @Test
    void test_invalidParameter_envelopeOverSixtyFourKibibytesRejected() {
        DomainEvent oversized = DomainEvent.builder().eventType(TestRun.LOOSE_EVENT).aggregate("TEST_NOTE", "990001")
            .aggregateVersion(1).occurredAt(java.time.Instant.parse("2026-10-17T10:00:00Z"))
            .sourceService(TestRun.PRODUCER).instanceId("main")
            .payload(JsonMapper.builder().build().createObjectNode().put("note", "x".repeat(66_000))).build();

        DomainEventPublishException exception = assertThrows(DomainEventPublishException.class,
            () -> publisher.publish(oversized));

        assertTrue(exception.getMessage().contains("64 KiB"), exception.getMessage());
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
        assertTrue(probe.invocations().isEmpty());
    }

    @Test
    void test_invalidParameter_payloadOutsideJsonObjectProfile() {
        DomainEvent decimal = DomainEvent.builder().eventType(TestRun.LOOSE_EVENT).aggregate("TEST_NOTE", "990002")
            .aggregateVersion(1).occurredAt(java.time.Instant.parse("2026-10-17T10:00:00Z"))
            .sourceService(TestRun.PRODUCER).instanceId("main")
            .payload(JsonMapper.builder().build().createObjectNode().put("rate", 1.5)).build();

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(decimal));
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
    }

    @Test
    void test_invalidParameter_sourceServiceMismatch() {
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(orphanEvent(TestRun.ORPHAN_PRODUCER)));
    }

    private RabbitDomainEventPublisher publisherFor(String producer) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return new RabbitDomainEventPublisher(producer, Duration.ofSeconds(5), new DomainEventCodec(), template,
            metrics);
    }

    private static DomainEvent orphanEvent(String producer) {
        return DomainEvent.builder().eventType(TestRun.LOOSE_EVENT).aggregate("TEST_NOTE", "770001")
            .aggregateVersion(1).occurredAt(java.time.Instant.parse("2026-10-17T10:00:00Z")).sourceService(producer)
            .instanceId("main").payload(JsonMapper.builder().build().createObjectNode().put("noteId", "770001"))
            .build();
    }

}
