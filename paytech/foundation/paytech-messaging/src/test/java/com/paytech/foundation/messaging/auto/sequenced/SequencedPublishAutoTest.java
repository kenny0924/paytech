package com.paytech.foundation.messaging.auto.sequenced;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.api.RegisteredCompensationEvent;
import com.paytech.foundation.compensation.core.execution.CompensationEventDispatcher;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.messaging.test.support.LocalRabbitEnvironment;
import com.paytech.foundation.messaging.test.support.TestRun;

/**
 * 发布侧按聚合串行（design/08 §3）与未路由 FAIL 重试，经真实补偿 Dispatcher、{@link AggregateKeySequencer}、
 * {@code RabbitDomainEventPublisher} 与本地 RabbitMQ / MySQL。
 * <li>同一聚合：先 Claim 的任务未完成前，后一个任务不开始，信封按登记顺序到达；不同聚合不被阻塞。</li>
 * <li>无订阅方时 FAIL 策略使发布失败，补偿任务转为可重试；出现订阅方后扫描重试成功。</li>
 */
@ActiveProfiles("mock")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = SequencedPublishTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"compensation.enabled=true", "compensation.instance-id=messaging-sequenced-test",
        "messaging.producer.sequencer.enabled=true", "messaging.producer.sequencer.lanes=4",
        "spring.sql.init.schema-locations=classpath:schema/compensation-event-test.sql"})
class SequencedPublishAutoTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    CompensationEventRegistrar registrar;

    @Autowired
    CompensationEventScanner scanner;

    @Autowired
    CompensationEventDispatcher dispatcher;

    @Autowired
    AggregateKeySequencer sequencer;

    @Autowired
    SequencedPublishProbe probe;

    @Autowired
    SequencedCompensationMapper mapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ConnectionFactory connectionFactory;

    @Autowired
    DomainEventCodec codec;

    private RabbitAdmin admin;
    private RabbitTemplate template;
    private String observer;

    @DynamicPropertySource
    static void localBroker(DynamicPropertyRegistry registry) {
        LocalRabbitEnvironment environment = LocalRabbitEnvironment.load();
        registry.add("spring.rabbitmq.host", () -> LocalRabbitEnvironment.HOST);
        registry.add("spring.rabbitmq.port", () -> LocalRabbitEnvironment.PORT);
        registry.add("spring.rabbitmq.username", environment::username);
        registry.add("spring.rabbitmq.password", environment::password);
        registry.add("spring.rabbitmq.virtual-host", environment::virtualHost);
        registry.add("messaging.producer.service", () -> TestRun.SEQUENCED_PRODUCER);
    }

    @BeforeEach
    void setUpBroker() {
        admin = new RabbitAdmin(connectionFactory);
        template = new RabbitTemplate(connectionFactory);
        observer = TestRun.PREFIX + "-sequenced-observer-" + UUID.randomUUID();
        mapper.deleteAll();
        probe.clear();
    }

    @AfterEach
    void tearDown() {
        await().atMost(WAIT).until(() -> sequencer.pendingTaskCount() == 0 && dispatcher.activeTaskCount() == 0
            && dispatcher.queuedTaskCount() == 0 && probe.inFlight() == 0);
        admin.deleteQueue(observer);
        mapper.deleteAll();
    }

    /** 本类拓扑只有发布方交换机与临时观察队列，结束时删除。 */
    @AfterAll
    void deleteTopology() {
        new RabbitAdmin(connectionFactory).deleteExchange(MessagingTopology.producerExchange(TestRun.SEQUENCED_PRODUCER));
    }

    /** 同一聚合两条事件：先登记的任务放慢，后一条仍在其后发布；另一聚合在此期间先发布。 */
    @Test
    void test_normalFlow_sameAggregatePublishedInVersionOrder() {
        bindObserver();
        String slow = eventKey("seq-a", 1);
        String next = eventKey("seq-a", 2);
        String other = eventKey(otherLaneAggregate("seq-a"), 1);
        probe.slowDown(slow, Duration.ofMillis(800));

        register(slow);
        await().atMost(WAIT).until(() -> probe.started().contains(slow));
        register(next);
        register(other);

        List<String> arrived = receive(3);
        assertEquals(List.of(other, slow, next), arrived);
        assertEquals(List.of(slow, other, next), probe.started());
    }

    /** 无订阅方：FAIL 抛错，任务转为可重试；绑定订阅方后扫描重试成功并删除任务。 */
    @Test
    void test_businessRuleViolation_unroutableFailsAndCompensationRetries() {
        String key = eventKey("seq-unroutable", 1);

        RegisteredCompensationEvent registered = register(key);

        await().atMost(WAIT).until(() -> "ERROR".equals(mapper.findStatus(registered.eventId())));
        assertEquals("TEST_UNPUBLISHED", mapper.findErrorCode(registered.eventId()));
        bindObserver();
        await().atMost(WAIT).until(() -> {
            scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of(SequencedPublishHandler.EVENT_TYPE)));
            return mapper.count(registered.eventId()) == 0;
        });
        assertEquals(List.of(key), receive(1));
        assertTrue(probe.started().size() >= 2, () -> "attempts=" + probe.started());
    }

    private RegisteredCompensationEvent register(String eventKey) {
        return new TransactionTemplate(transactionManager).execute(status -> registrar.register(
            new CompensationEventRequest(eventKey, SequencedPublishHandler.EVENT_TYPE, "-")));
    }

    private void bindObserver() {
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者；排他队列随连接关闭删除，x-expires 兜底。
        admin.declareQueue(QueueBuilder.nonDurable(observer).exclusive().expires(600_000).build());
        admin.declareBinding(new org.springframework.amqp.core.Binding(observer,
            org.springframework.amqp.core.Binding.DestinationType.QUEUE,
            MessagingTopology.producerExchange(TestRun.SEQUENCED_PRODUCER), "", java.util.Map.of()));
    }

    /** 按到达顺序读取 n 条信封，返回其事件键；之后不应再有消息。 */
    private List<String> receive(int count) {
        List<String> keys = new ArrayList<>();
        while (keys.size() < count) {
            Message message = template.receive(observer, WAIT.toMillis());
            if (message == null) {
                throw new AssertionError("信封未到达，已收到: " + keys);
            }
            keys.add(codec.decode(message.getBody()).eventKey());
        }
        assertNull(template.receive(observer, 300));
        return keys;
    }

    /** 与给定聚合落在不同串行线程上的另一个聚合。 */
    private String otherLaneAggregate(String aggregateId) {
        int lane = sequencer.laneOf(eventKey(aggregateId, 1));
        for (int index = 0; index < 100; index++) {
            String candidate = "seq-b" + index;
            if (sequencer.laneOf(eventKey(candidate, 1)) != lane) {
                return candidate;
            }
        }
        throw new AssertionError("no aggregate on another lane");
    }

    private static String eventKey(String aggregateId, long version) {
        return AggregateKey.of("TEST_FACT", TestRun.PREFIX + aggregateId).eventKey(TestRun.STRICT_EVENT, version);
    }
}
