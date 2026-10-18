package com.paytech.foundation.messaging.auto.consume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.consume.DomainEventMessageListener;
import com.paytech.foundation.messaging.test.base.MessagingMockTestBase;
import com.paytech.foundation.messaging.test.support.TestRun;

/**
 * 分区有序消费与消费幂等（design/08 §3–§4），经真实发布端、本地 RabbitMQ、分区监听容器与 MySQL。
 *
 * @see DomainEventMessageListener#onMessage(org.springframework.amqp.core.Message, com.rabbitmq.client.Channel)
 * @see com.paytech.foundation.messaging.consume.IdempotentConsumeInterceptor#consume(DomainEvent,
 *      com.paytech.foundation.messaging.consume.DomainEventHandlerRegistry.Registration)
 */
class PartitionedConsumeAutoTest extends MessagingMockTestBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    void test_normalFlow_sameAggregateConsumedInOrder() {
        String aggregateId = uniqueAggregate();

        for (long version = 1; version <= 5; version++) {
            publisher.publish(strictEvent(aggregateId, version));
        }

        awaitHandled(aggregateId, 5);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), testMapper.findHandledVersions(aggregateId));
        assertEquals(5L, testMapper.findLastVersion("TEST_FACT", aggregateId));
        assertEquals(5, testMapper.countConsumedEventsOfAggregate(aggregateId));
        assertEquals(5.0, consumed(TestRun.STRICT_EVENT, "handled"));
        assertEquals(5.0, published(TestRun.STRICT_EVENT));
    }

    @Test
    void test_normalFlow_differentAggregatesConsumedInParallel() {
        probe.slowDown(Duration.ofMillis(400));
        List<String> aggregates = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            aggregates.add(uniqueAggregate());
        }

        aggregates.forEach(aggregateId -> publisher.publish(looseEvent(aggregateId, 1)));

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.LOOSE_EVENT, "handled") == aggregates.size());
        assertTrue(probe.maxInFlight() >= 2, () -> "maxInFlight=" + probe.maxInFlight());
        assertTrue(probe.threadCount() >= 2, () -> "threads=" + probe.threadCount());
        aggregates.forEach(aggregateId -> assertEquals(List.of(1L), testMapper.findHandledVersions(aggregateId)));
    }

    @Test
    void test_businessRuleViolation_replayedEventSkipped() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = looseEvent(aggregateId, 1);

        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        publisher.publish(event);

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.LOOSE_EVENT, "replayed") == 1.0);
        assertEquals(List.of(1L), testMapper.findHandledVersions(aggregateId));
        assertEquals(1, testMapper.countConsumedEvent(event.eventId()));
    }

    @Test
    void test_businessRuleViolation_staleVersionSkipped() {
        String aggregateId = uniqueAggregate();

        publisher.publish(looseEvent(aggregateId, 3));
        awaitHandled(aggregateId, 1);
        publisher.publish(looseEvent(aggregateId, 2));

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.LOOSE_EVENT, "stale") == 1.0);
        assertEquals(List.of(3L), testMapper.findHandledVersions(aggregateId));
        assertEquals(3L, testMapper.findLastVersion("TEST_NOTE", aggregateId));
        assertEquals(0, testMapper.countConsumedEvent(looseEvent(aggregateId, 2).eventId()));
    }

    @Test
    void test_edgeCase_looseHandlerProcessesAcrossGap() {
        String aggregateId = uniqueAggregate();

        publisher.publish(looseEvent(aggregateId, 1));
        awaitHandled(aggregateId, 1);
        publisher.publish(looseEvent(aggregateId, 4));

        awaitHandled(aggregateId, 2);
        assertEquals(List.of(1L, 4L), testMapper.findHandledVersions(aggregateId));
        assertEquals(0.0, consumed(TestRun.LOOSE_EVENT, "gap"));
    }

    @Test
    void test_businessRuleViolation_gapWaitsThenDeadLetters() {
        String aggregateId = uniqueAggregate();

        publisher.publish(strictEvent(aggregateId, 1));
        awaitHandled(aggregateId, 1);
        DomainEvent gapEvent = strictEvent(aggregateId, 3);
        publisher.publish(gapEvent);

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.STRICT_EVENT, "dead") == 1.0);
        Message deadLetter = awaitDeadLetter();
        assertEquals(gapEvent.eventId(), deadLetter.getMessageProperties().getMessageId());
        assertEquals(5.0, consumed(TestRun.STRICT_EVENT, "gap"));
        assertEquals(List.of(1L), testMapper.findHandledVersions(aggregateId));
        assertEquals(1L, testMapper.findLastVersion("TEST_FACT", aggregateId));
        assertEquals(0, testMapper.countConsumedEvent(gapEvent.eventId()));
    }

    @Test
    void test_edgeCase_gapFilledByLaterDeliveryBeforeLimit() {
        String aggregateId = uniqueAggregate();

        publisher.publish(strictEvent(aggregateId, 1));
        awaitHandled(aggregateId, 1);
        publisher.publish(strictEvent(aggregateId, 3));
        publisher.publish(strictEvent(aggregateId, 2));

        awaitHandled(aggregateId, 3);
        assertEquals(List.of(1L, 2L, 3L), testMapper.findHandledVersions(aggregateId));
        assertTrue(consumed(TestRun.STRICT_EVENT, "gap") >= 1.0);
        assertEquals(0.0, consumed(TestRun.STRICT_EVENT, "dead"));
        assertEquals(3L, testMapper.findLastVersion("TEST_FACT", aggregateId));
    }

    @Test
    void test_businessRuleViolation_handlerFailureRollsBackAndDeadLetters() {
        String aggregateId = uniqueAggregate();
        probe.failAggregate(aggregateId);

        publisher.publish(looseEvent(aggregateId, 1));

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.LOOSE_EVENT, "dead") == 1.0);
        assertNotNull(awaitDeadLetter());
        assertEquals(5, probe.invocations().stream().filter((aggregateId + "@1")::equals).count());
        assertEquals(List.of(), testMapper.findHandledVersions(aggregateId));
        assertEquals(0, testMapper.countConsumedEventsOfAggregate(aggregateId));
    }

    @Test
    void test_normalFlow_handlerManagedTransactionRunsOutsideConsumeTransaction() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = managedEvent(aggregateId, 2);

        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        Awaitility.await().atMost(WAIT).until(() -> testMapper.countConsumedEvent(event.eventId()) == 1);
        publisher.publish(event);
        publisher.publish(managedEvent(aggregateId, 1));

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.MANAGED_EVENT, "replayed") == 1.0
            && consumed(TestRun.MANAGED_EVENT, "stale") == 1.0);
        assertEquals(List.of(false), probe.transactionActive());
        assertEquals(List.of(2L), testMapper.findHandledVersions(aggregateId));
        assertEquals(2L, testMapper.findLastVersion("TEST_ENTRY", aggregateId));
        assertEquals(1, testMapper.countConsumedEventsOfAggregate(aggregateId));
    }

    @Test
    void test_businessRuleViolation_handlerManagedFailureLeavesNoConsumeRecord() {
        String aggregateId = uniqueAggregate();
        probe.failAggregate(aggregateId);

        publisher.publish(managedEvent(aggregateId, 1));

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.MANAGED_EVENT, "dead") == 1.0);
        assertNotNull(awaitDeadLetter());
        assertEquals(List.of(), testMapper.findHandledVersions(aggregateId));
        assertEquals(0, testMapper.countConsumedEventsOfAggregate(aggregateId));
        assertEquals(null, testMapper.findLastVersion("TEST_ENTRY", aggregateId));
    }

    @Test
    void test_businessRuleViolation_rejectedEventDeadLetteredWithoutRetry() {
        String aggregateId = uniqueAggregate();
        probe.rejectAggregate(aggregateId);
        DomainEvent event = managedEvent(aggregateId, 1);

        publisher.publish(event);

        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.MANAGED_EVENT, "dead") == 1.0);
        Message deadLetter = awaitDeadLetter();
        assertEquals(event.eventId(), deadLetter.getMessageProperties().getMessageId());
        assertEquals(1, probe.transactionActive().size(), "拒绝不重投");
        assertEquals(0, testMapper.countConsumedEventsOfAggregate(aggregateId));
    }

    @Test
    void test_invalidParameter_malformedEnvelopeDeadLettered() {
        broker.sendRaw(broker.partitionQueues().getFirst(), "{\"eventId\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        Awaitility.await().atMost(WAIT).until(() -> consumed("UNKNOWN", "dead") == 1.0);
        assertNotNull(awaitDeadLetter());
        assertEquals(List.of(), probe.invocations());
    }

    @Test
    void test_edgeCase_unsubscribedEventTypeAcknowledged() {
        String aggregateId = uniqueAggregate();

        publisher.publish(event(TestRun.UNSUBSCRIBED_EVENT, "TEST_AUDIT", aggregateId, 1));
        // 同一聚合落在同一分区：后发的已订阅事件被处理，说明先到的未订阅事件已被确认。
        publisher.publish(looseEvent(aggregateId, 1));

        awaitHandled(aggregateId, 1);
        assertEquals(List.of(aggregateId + "@1"), probe.invocations());
        assertEquals(1, testMapper.countConsumedEventsOfAggregate(aggregateId));
        assertEquals(null, broker.receiveDeadLetter());
    }

    private DomainEvent managedEvent(String aggregateId, long version) {
        return event(TestRun.MANAGED_EVENT, "TEST_ENTRY", aggregateId, version);
    }

    private void awaitHandled(String aggregateId, int count) {
        Awaitility.await().atMost(WAIT).until(() -> testMapper.findHandledVersions(aggregateId).size() == count);
    }

    private Message awaitDeadLetter() {
        Message[] holder = new Message[1];
        Awaitility.await().atMost(WAIT).until(() -> (holder[0] = broker.receiveDeadLetter()) != null);
        return holder[0];
    }

    private static String uniqueAggregate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
