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
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublishException;
import com.paytech.foundation.messaging.consume.PartitionedListenerContainers;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyRecovery;
import com.paytech.foundation.messaging.test.base.MessagingMockTestBase;
import com.paytech.foundation.messaging.test.support.BrokerTopologyProbe;
import com.paytech.foundation.messaging.test.support.TestRun;
import com.paytech.foundation.messaging.test.support.TopologyLogProbe;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.databind.JsonNode;

/**
 * Broker 丢失拓扑后按原参数幂等重声明本服务的发布与订阅拓扑，监听容器恢复消费（需求 2026-10-18，任务卡 01、02）。
 * <li>连接重建：以 {@link CachingConnectionFactory#resetConnection()} 模拟 Broker 重建后的断连；重声明在新连接创建线程上同步完成，
 * 因此 {@code createConnection()} 返回后即可断言拓扑。只验证这条路径的用例先停止监听容器，避免容器的缺失队列事件抢先恢复。</li>
 * <li>连接保持：分区队列被删时由容器的消费者取消与缺失队列事件触发重声明；发布方交换机被删时由发布被拒触发重声明。</li>
 * <li>失败重试：单项声明失败按 {@code messaging.recovery.*} 有界指数退避只重试失败项及其依赖绑定，每次失败记 WARN，
 * 连续失败到阈值只记一次 ERROR。测试配置把间隔缩短到毫秒级、阈值设为 3。</li>
 *
 * @see MessagingTopologyRecovery#onCreate(org.springframework.amqp.rabbit.connection.Connection)
 */
class MessagingTopologyRecoveryAutoTest extends MessagingMockTestBase {

    private static final Duration WAIT = Duration.ofSeconds(30);

    /** 与 application-mock.yaml 的 messaging.recovery.error-threshold 一致。 */
    private static final int ERROR_THRESHOLD = 3;

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
    private PartitionedListenerContainers containers;

    @Autowired
    private BrokerTopologyProbe topology;

    @Autowired
    private TopologyLogProbe topologyLog;

    private String conflictingQueue;

    /** 删除本例制造的冲突队列、恢复全部监听容器，并经启动声明路径恢复本次运行拓扑，使全局清理与后续用例面对完整拓扑。 */
    @AfterEach
    void restoreRunTopology() {
        if (conflictingQueue != null) {
            broker.deleteQueue(conflictingQueue);
            conflictingQueue = null;
        }
        declarer.declareProducer(TestRun.PRODUCER);
        declarer.declareSubscription(TestRun.CONSUMER, TestRun.PRODUCER, TestRun.PARTITIONS);
        containers.containers().stream().filter(container -> !container.isRunning())
            .forEach(SimpleMessageListenerContainer::start);
    }

    /** Broker 重建：全部拓扑丢失时发布被拒；重连后按原参数恢复，同一事件重发后被消费一次。 */
    @Test
    void test_normalFlow_lostTopologyRedeclaredOnReconnect() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = strictEvent(aggregateId, 1);
        stopContainers();
        broker.deleteRunTopology();
        assertTopologyAbsent();
        assertThrows(DomainEventPublishException.class, () -> publisher.publish(event));

        reconnect();

        assertTopologyRestored(allPartitions());
        startContainers();
        assertTrue(topologyLog.errors().isEmpty(), () -> "unexpected errors: " + messages(topologyLog.errors()));
        assertTrue(topologyLog.warnings().isEmpty(), () -> "unexpected warnings: " + messages(topologyLog.warnings()));
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
        assertTrue(topologyLog.errors().isEmpty(), () -> "unexpected errors: " + messages(topologyLog.errors()));
    }

    /** 订阅侧拓扑丢失、发布方交换机仍在：发布按默认 FAIL 未路由策略抛出且不计成功；重连恢复后补偿重发同一事件被消费一次。 */
    @Test
    void test_businessRuleViolation_unroutableBeforeRecoveryRetriedAfterReconnect() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = looseEvent(aggregateId, 1);
        stopContainers();
        broker.deleteSubscriptionTopology();

        DomainEventPublishException exception = assertThrows(DomainEventPublishException.class,
            () -> publisher.publish(event));

        assertTrue(exception.getMessage().contains("未路由"), exception.getMessage());
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
        assertEquals(0.0, published(TestRun.LOOSE_EVENT, "unroutable"));
        reconnect();
        assertTopologyRestored(allPartitions());
        startContainers();
        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        assertEquals(1, testMapper.countConsumedEvent(event.eventId()));
        assertEquals(1.0, published(TestRun.LOOSE_EVENT));
        assertEquals(List.of(aggregateId + "@1"), probe.invocations());
    }

    /**
     * 同名经典队列冲突：恢复时只有该队列失败并记 WARN、不绑定到哈希交换机，其余拓扑恢复；按退避重试且连续失败到阈值只记一次 ERROR；
     * 删除冲突队列后不重建连接，由重试补回 Quorum 分区队列及其绑定，事件经全部分区消费。
     */
    @Test
    void test_edgeCase_conflictingQueueRetriedUntilResolved() {
        int conflictingPartition = 1;
        conflictingQueue = partitionQueue(conflictingPartition);
        SimpleMessageListenerContainer conflictingContainer = container(conflictingPartition);
        conflictingContainer.stop();
        broker.deleteQueue(conflictingQueue);
        broker.declareClassicQueue(conflictingQueue);
        conflictingContainer.start();

        reconnect();

        assertConflictIsolated(conflictingPartition);
        Awaitility.await().atMost(WAIT).until(() -> topologyLog.errors().size() == 1);
        int warningsAtError = topologyLog.warnings().size();
        assertTrue(warningsAtError >= ERROR_THRESHOLD, () -> messages(topologyLog.warnings()).toString());
        Awaitility.await().atMost(WAIT).until(() -> topologyLog.warnings().size() > warningsAtError);
        assertEquals(1, topologyLog.errors().size(), () -> messages(topologyLog.errors()).toString());
        assertTrue(topologyLog.errors().get(0).getFormattedMessage().contains(conflictingQueue),
            topologyLog.errors().get(0).getFormattedMessage());
        broker.deleteQueue(conflictingQueue);
        conflictingQueue = null;

        Awaitility.await().atMost(WAIT).pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyRestored(allPartitions()));
        assertConsumedThroughPartition(conflictingPartition);
        assertEquals(1, topologyLog.errors().size(), () -> messages(topologyLog.errors()).toString());
    }

    /**
     * 连接保持、某个分区队列被删除：消费者被 Broker 取消后自动重声明该订阅；原经该分区的聚合后续事件被消费一次，重建的分区继续承接新事件。
     * <li>一致性哈希环随绑定重建而变化，同一聚合在重建后可能落到其他分区，因此只断言消费一次而不断言分区。</li>
     */
    @Test
    void test_edgeCase_partitionQueueDeletedWhileConnected() {
        int deletedPartition = 2;
        String aggregateId = aggregateOnPartition(deletedPartition);

        broker.deleteQueue(partitionQueue(deletedPartition));

        Awaitility.await().atMost(WAIT).pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyRestored(allPartitions()));
        DomainEvent next = strictEvent(aggregateId, 2);
        publisher.publish(next);
        awaitHandled(aggregateId, 2);
        assertEquals(1, testMapper.countConsumedEvent(next.eventId()));
        assertConsumedThroughPartition(deletedPartition);
        assertTrue(topologyLog.errors().isEmpty(), () -> "unexpected errors: " + messages(topologyLog.errors()));
        assertTrue(topologyLog.warnings().isEmpty(), () -> "unexpected warnings: " + messages(topologyLog.warnings()));
    }

    /** 连接保持、发布方交换机被删除：当次发布被 Broker 拒绝并按 FAIL 抛出，同时重声明交换机与订阅方绑定；再次发布成功并被消费一次。 */
    @Test
    void test_businessRuleViolation_producerExchangeDeletedWhileConnected() {
        String aggregateId = uniqueAggregate();
        DomainEvent event = looseEvent(aggregateId, 1);
        broker.deleteExchange(PRODUCER_EXCHANGE);
        assertNull(topology.exchange(PRODUCER_EXCHANGE));

        DomainEventPublishException exception = assertThrows(DomainEventPublishException.class,
            () -> publisher.publish(event));

        assertTrue(exception.getMessage().contains("交换机缺失"), exception.getMessage());
        assertEquals(0.0, published(TestRun.LOOSE_EVENT));
        assertExchange(PRODUCER_EXCHANGE, "fanout");
        assertEquals(1, topology.exchangeBindings(PRODUCER_EXCHANGE, HASH_EXCHANGE).size());
        publisher.publish(event);
        awaitHandled(aggregateId, 1);
        assertEquals(1, testMapper.countConsumedEvent(event.eventId()));
        assertEquals(1.0, published(TestRun.LOOSE_EVENT));
        assertTrue(topologyLog.errors().isEmpty(), () -> "unexpected errors: " + messages(topologyLog.errors()));
    }

    private void assertConflictIsolated(int conflictingPartition) {
        JsonNode conflicting = topology.queue(conflictingQueue);
        assertNotNull(conflicting);
        assertEquals("classic", conflicting.path("type").asString());
        assertTrue(topology.queueBindings(HASH_EXCHANGE, conflictingQueue).isEmpty());
        assertTopologyRestored(IntStream.range(0, TestRun.PARTITIONS).filter(partition -> partition != conflictingPartition)
            .boxed().toList());
        List<ILoggingEvent> failures = topologyLog.warnings().stream()
            .filter(warning -> warning.getThrowableProxy() != null).toList();
        assertTrue(!failures.isEmpty(), () -> messages(topologyLog.warnings()).toString());
        failures.forEach(failure -> assertTrue(failure.getFormattedMessage().contains(conflictingQueue),
            failure.getFormattedMessage()));
    }

    /** 分批发布新聚合，断言每个都被消费一次，直到有一个经指定分区的容器线程处理；哈希环分布不均，因此分批而非固定数量。 */
    private void assertConsumedThroughPartition(int partition) {
        for (int batch = 0; batch < 10; batch++) {
            List<String> aggregates = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                aggregates.add(uniqueAggregate());
            }
            aggregates.forEach(aggregateId -> publisher.publish(looseEvent(aggregateId, 1)));
            aggregates.forEach(aggregateId -> awaitHandled(aggregateId, 1));
            if (aggregates.stream().anyMatch(aggregateId -> onPartition(aggregateId, 1, partition))) {
                return;
            }
        }
        throw new AssertionError("no event consumed through partition " + partition);
    }

    /** 逐个发布新聚合的第一个版本，返回第一个经指定分区消费的聚合。 */
    private String aggregateOnPartition(int partition) {
        for (int index = 0; index < 64; index++) {
            String aggregateId = uniqueAggregate();
            publisher.publish(strictEvent(aggregateId, 1));
            awaitHandled(aggregateId, 1);
            if (onPartition(aggregateId, 1, partition)) {
                return aggregateId;
            }
        }
        throw new AssertionError("no aggregate routed to partition " + partition);
    }

    private boolean onPartition(String aggregateId, long version, int partition) {
        String thread = probe.thread(aggregateId, version);
        return thread != null && thread.startsWith(partitionQueue(partition) + "-");
    }

    private SimpleMessageListenerContainer container(int partition) {
        String queue = partitionQueue(partition);
        return containers.containers().stream().filter(container -> queue.equals(container.getQueueNames()[0]))
            .findFirst().orElseThrow(() -> new AssertionError("no container for " + queue));
    }

    private void stopContainers() {
        containers.containers().forEach(SimpleMessageListenerContainer::stop);
    }

    private void startContainers() {
        containers.containers().forEach(SimpleMessageListenerContainer::start);
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
        String name = partitionQueue(partition);
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

    private static String partitionQueue(int partition) {
        return MessagingTopology.partitionQueue(TestRun.CONSUMER, TestRun.PRODUCER, partition);
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
