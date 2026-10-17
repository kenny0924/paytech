package com.paytech.foundation.messaging.test.base;

import java.time.Duration;
import java.time.Instant;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.foundation.messaging.TestApplication;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.foundation.messaging.test.mapper.TestMapper;
import com.paytech.foundation.messaging.test.support.BrokerTestSupport;
import com.paytech.foundation.messaging.test.support.HandledEventProbe;
import com.paytech.foundation.messaging.test.support.LocalRabbitEnvironment;
import com.paytech.foundation.messaging.test.support.TestRun;
import com.paytech.foundation.testsupport.api.TestBase;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * messaging 的本地 Broker + MySQL 测试基类。
 * <li>Broker 账号从 {@code paytech.local-environment.dir}/.env.local 读取；拓扑名带本次运行前缀。</li>
 * <li>每例结束先等待分区消费空闲，再由 {@link TestBase} 统一清理数据库、队列、观察器与计数器。</li>
 */
@ActiveProfiles("mock")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class MessagingMockTestBase extends TestBase {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    protected TestMapper testMapper;

    @Autowired
    protected HandledEventProbe probe;

    @Autowired
    protected MeterRegistry meterRegistry;

    @Autowired
    protected BrokerTestSupport broker;

    @Autowired
    protected DomainEventPublisher publisher;

    @DynamicPropertySource
    static void localBroker(DynamicPropertyRegistry registry) {
        LocalRabbitEnvironment environment = LocalRabbitEnvironment.load();
        registry.add("spring.rabbitmq.host", () -> LocalRabbitEnvironment.HOST);
        registry.add("spring.rabbitmq.port", () -> LocalRabbitEnvironment.PORT);
        registry.add("spring.rabbitmq.username", environment::username);
        registry.add("spring.rabbitmq.password", environment::password);
        registry.add("spring.rabbitmq.virtual-host", environment::virtualHost);
        registry.add("messaging.producer.service", () -> TestRun.PRODUCER);
        registry.add("messaging.consumer.service", () -> TestRun.CONSUMER);
        registry.add("messaging.consumers[" + TestRun.PRODUCER + "].partitions", () -> TestRun.PARTITIONS);
    }

    @BeforeEach
    final void requireEmptyBaseline() {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(this::consumersIdle);
    }

    @AfterEach
    final void awaitConsumersIdle() {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(this::consumersIdle);
    }

    /**
     * 构造本次运行发布方的测试事件。
     *
     * @param eventType 事件类型
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合
     * @param version 版本
     * @return 信封
     */
    protected DomainEvent event(String eventType, String aggregateType, String aggregateId, long version) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("aggregateId", aggregateId);
        payload.put("note", "v" + version);
        return DomainEvent.builder().eventType(eventType).aggregate(aggregateType, aggregateId)
            .aggregateVersion(version).occurredAt(Instant.parse("2026-10-17T10:00:00.123456Z"))
            .sourceService(TestRun.PRODUCER).instanceId("main").payload(payload).build();
    }

    /**
     * 严格有序事件。
     *
     * @param aggregateId 聚合
     * @param version 版本
     * @return 信封
     */
    protected DomainEvent strictEvent(String aggregateId, long version) {
        return event(TestRun.STRICT_EVENT, "TEST_FACT", aggregateId, version);
    }

    /**
     * 非严格事件。
     *
     * @param aggregateId 聚合
     * @param version 版本
     * @return 信封
     */
    protected DomainEvent looseEvent(String aggregateId, long version) {
        return event(TestRun.LOOSE_EVENT, "TEST_NOTE", aggregateId, version);
    }

    /**
     * 某事件类型某结果的消费计数。
     *
     * @param eventType 事件类型
     * @param outcome 结果标签
     * @return 计数
     */
    protected double consumed(String eventType, String outcome) {
        Counter counter = meterRegistry.find("messaging.consumed").tag("eventType", eventType).tag("outcome", outcome)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    /**
     * 某事件类型已确认且已路由的发布计数。
     *
     * @param eventType 事件类型
     * @return 计数
     */
    protected double published(String eventType) {
        return published(eventType, "confirmed");
    }

    /**
     * 某事件类型按发布结果的计数。
     *
     * @param eventType 事件类型
     * @param outcome 发布结果：confirmed 或 unroutable
     * @return 计数
     */
    protected double published(String eventType, String outcome) {
        Counter counter = meterRegistry.find("messaging.published").tag("eventType", eventType).tag("outcome", outcome)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    private boolean consumersIdle() {
        return probe.inFlight() == 0 && broker.partitionQueues().stream().allMatch(queue -> broker.readyMessages(queue) == 0);
    }
}
