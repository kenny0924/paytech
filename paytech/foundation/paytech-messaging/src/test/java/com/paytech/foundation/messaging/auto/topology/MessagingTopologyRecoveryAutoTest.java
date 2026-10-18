package com.paytech.foundation.messaging.auto.topology;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyRecovery;
import com.paytech.foundation.messaging.test.base.MessagingMockTestBase;
import com.paytech.foundation.messaging.test.support.BrokerTopologyProbe;
import com.paytech.foundation.messaging.test.support.TestRun;
import com.paytech.foundation.messaging.test.support.TopologyErrorLogProbe;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.databind.JsonNode;

/**
 * Broker 丢失拓扑后，连接重建即按原参数幂等重声明本服务的发布与订阅拓扑，监听容器恢复消费；恢复前的发布按既有策略失败，
 * 参数冲突只让冲突项失败并记 ERROR。
 * <li>以 {@link CachingConnectionFactory#resetConnection()} 模拟 Broker 重建后的断连；重声明在新连接创建线程上同步完成，
 * 因此 {@code createConnection()} 返回后即可断言拓扑。</li>
 *
 * @see MessagingTopologyRecovery#onCreate(org.springframework.amqp.rabbit.connection.Connection)
 */
class MessagingTopologyRecoveryAutoTest extends MessagingMockTestBase {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private static final String PRODUCER_EXCHANGE = MessagingTopology.producerExchange(TestRun.PRODUCER);

    private static final String HASH_EXCHANGE = MessagingTopology.consumerHashExchange(TestRun.CONSUMER,
        TestRun.PRODUCER);

    private static final String DEAD_LETTER_EXCHANGE = MessagingTopology.deadLetterExchange(TestRun.CONSUMER,
        TestRun.PRODUCER);

    private static final String DEAD_LETTER_QUEUE = MessagingTopology.deadLetterQueue(TestRun.CONSUMER,
        TestRun.PRODUCER);

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Autowired
    private MessagingTopologyDeclarer declarer;

    @Autowired
    private BrokerTopologyProbe topology;

    @Autowired
    private TopologyErrorLogProbe errorLog;

    private String conflictingQueue;

    /** 删除本例制造的冲突队列，并经启动声明路径恢复本次运行拓扑，使全局清理与后续用例面对完整拓扑。 */
    @AfterEach
    void restoreRunTopology() {
        if (conflictingQueue != null) {
            broker.deleteQueue(conflictingQueue);
            conflictingQueue = null;
        }
        declarer.declareProducer(TestRun.PRODUCER);
        declarer.declareSubscription(TestRun.CONSUMER, TestRun.PRODUCER, TestRun.PARTITIONS);
    }

    /** Broker 重建：全部拓扑丢失时发布被拒；重连后按原参数恢复，同一事件重发后被消费一次。 */
    @Test
    void test_normalFlow_lostTopologyRedeclaredOnReconnect() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = strictEvent(aggregateId, 1);
        broker.deleteRunTopology();
        assertTopologyAbsent();
        assertThrows(DomainEventPublishException.class, () -> publisher.publish(event));

        reconnect();

        assertTopologyRestored(allPartitions());
        assertTrue(errorLog.errors().isEmpty(), () -> "unexpected errors: " + messages(errorLog.errors()));
        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        assertEquals(1, testMapper.countConsumedEvent(event.eventId()));
        assertEquals(1.0, published(TestRun.STRICT_EVENT));
        assertEquals(1.0, consumed(TestRun.STRICT_EVENT, "handled"));
    }

    /** 无调用方主动建连：监听容器重启消费者时自行建连并触发重声明，随后恢复消费（Broker 重建后的实际路径）。 */
    @Test
    void test_normalFlow_listenerContainersReconnectAndRedeclare() {
        String aggregateId = uniqueAggregate();
        broker.deleteRunTopology();

        connectionFactory.resetConnection();

        Awaitility.await().atMost(WAIT).pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> assertTopologyRestored(allPartitions()));
        publisher.publish(strictEvent(aggregateId, 1));
        awaitHandled(aggregateId, 1);
        assertEquals(List.of(aggregateId + "@1"), probe.invocations());
        assertTrue(errorLog.errors().isEmpty(), () -> "unexpected errors: " + messages(errorLog.errors()));
    }

    /** 订阅侧拓扑丢失、发布方交换机仍在：发布按默认 FAIL 未路由策略抛出且不计成功；重连恢复后补偿重发同一事件被消费一次。 */
    @Test
    void test_businessRuleViolation_unroutableBeforeRecoveryRetriedAfterReconnect() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = looseEvent(aggregateId, 1);
        broker.deleteSubscriptionTopology();

        DomainEventPublishException exception = assertThrows(DomainEventPublishException.class,
            () -> publisher.publish(event));

        assertTrue(exception.getMessage().contains("未路由"), exception.getMessage());
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
        assertEquals(0.0, published(TestRun.LOOSE_EVENT, "unroutable"));
        reconnect();
        assertTopologyRestored(allPartitions());
        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        assertEquals(1, testMapper.countConsumedEvent(event.eventId()));
        assertEquals(1.0, published(TestRun.LOOSE_EVENT));
        assertEquals(List.of(aggregateId + "@1"), probe.invocations());
    }

    /** 同名经典队列与分区队列参数冲突：只有该队列声明失败并记 ERROR、不绑定到哈希交换机；其余拓扑恢复，事件经其余分区消费。 */
    @Test
    void test_edgeCase_conflictingQueueFailsAloneWithErrorLog() {
        int conflictingPartition = 1;
        broker.deleteRunTopology();
        conflictingQueue = MessagingTopology.partitionQueue(TestRun.CONSUMER, TestRun.PRODUCER, conflictingPartition);
        broker.declareClassicQueue(conflictingQueue);

        reconnect();

        JsonNode conflicting = topology.queue(conflictingQueue);
        assertNotNull(conflicting);
        assertEquals("classic", conflicting.path("type").asString());
        assertTrue(topology.queueBindings(HASH_EXCHANGE, conflictingQueue).isEmpty());
        assertTopologyRestored(IntStream.range(0, TestRun.PARTITIONS).filter(partition -> partition != conflictingPartition)
            .boxed().toList());
        List<ILoggingEvent> declarationFailures = errorLog.errors().stream()
            .filter(error -> error.getThrowableProxy() != null).toList();
        assertEquals(1, declarationFailures.size(), () -> messages(errorLog.errors()).toString());
        assertTrue(declarationFailures.get(0).getFormattedMessage().contains(conflictingQueue),
            declarationFailures.get(0).getFormattedMessage());
        assertTrue(errorLog.errors().stream().anyMatch(error -> error.getThrowableProxy() == null
            && error.getFormattedMessage().contains("失败 1 项")), () -> messages(errorLog.errors()).toString());
        List<String> aggregates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            aggregates.add(uniqueAggregate());
        }
        aggregates.forEach(aggregateId -> publisher.publish(looseEvent(aggregateId, 1)));
        Awaitility.await().atMost(WAIT).until(() -> consumed(TestRun.LOOSE_EVENT, "handled") == aggregates.size());
        assertEquals(0, broker.readyMessages(conflictingQueue));
    }

    private void reconnect() {
        connectionFactory.resetConnection();
        connectionFactory.createConnection();
    }

    private void assertTopologyAbsent() {
        assertAll(
            () -> assertNull(topology.exchange(PRODUCER_EXCHANGE)),
            () -> assertNull(topology.exchange(HASH_EXCHANGE)),
            () -> assertNull(topology.exchange(DEAD_LETTER_EXCHANGE)),
            () -> assertNull(topology.queue(DEAD_LETTER_QUEUE)),
            () -> broker.partitionQueues().forEach(queue -> assertNull(topology.queue(queue), queue)));
    }

    private void assertTopologyRestored(List<Integer> partitions) {
        assertExchange(PRODUCER_EXCHANGE, "fanout");
        assertExchange(HASH_EXCHANGE, MessagingTopology.CONSISTENT_HASH_TYPE);
        assertExchange(DEAD_LETTER_EXCHANGE, "fanout");
        assertEquals(1, topology.exchangeBindings(PRODUCER_EXCHANGE, HASH_EXCHANGE).size());
        JsonNode deadLetterQueue = topology.queue(DEAD_LETTER_QUEUE);
        assertNotNull(deadLetterQueue);
        assertEquals("quorum", deadLetterQueue.path("type").asString());
        assertTrue(deadLetterQueue.path("durable").asBoolean());
        assertEquals(1, topology.queueBindings(DEAD_LETTER_EXCHANGE, DEAD_LETTER_QUEUE).size());
        partitions.forEach(this::assertPartitionQueue);
    }

    private void assertExchange(String name, String type) {
        JsonNode exchange = topology.exchange(name);
        assertNotNull(exchange, name);
        assertEquals(type, exchange.path("type").asString(), name);
        assertTrue(exchange.path("durable").asBoolean(), name);
        assertTrue(!exchange.path("auto_delete").asBoolean(), name);
    }

    private void assertPartitionQueue(int partition) {
        String name = MessagingTopology.partitionQueue(TestRun.CONSUMER, TestRun.PRODUCER, partition);
        JsonNode queue = topology.queue(name);
        assertNotNull(queue, name);
        JsonNode arguments = queue.path("arguments");
        assertAll(name,
            () -> assertEquals("quorum", queue.path("type").asString()),
            () -> assertTrue(queue.path("durable").asBoolean()),
            () -> assertTrue(arguments.path("x-single-active-consumer").asBoolean()),
            () -> assertEquals(MessagingTopology.DELIVERY_LIMIT, arguments.path("x-delivery-limit").asInt()),
            () -> assertEquals(DEAD_LETTER_EXCHANGE, arguments.path("x-dead-letter-exchange").asString()));
        List<JsonNode> bindings = topology.queueBindings(HASH_EXCHANGE, name);
        assertEquals(1, bindings.size(), name);
        assertEquals(MessagingTopology.PARTITION_WEIGHT, bindings.get(0).path("routing_key").asString(), name);
    }

    private void awaitHandled(String aggregateId, int count) {
        Awaitility.await().atMost(WAIT).until(() -> testMapper.findHandledVersions(aggregateId).size() == count);
    }

    private static List<Integer> allPartitions() {
        return IntStream.range(0, TestRun.PARTITIONS).boxed().toList();
    }

    private static List<String> messages(List<ILoggingEvent> events) {
        return events.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static String uniqueAggregate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
