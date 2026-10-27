package com.paytech.compliance.test.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * compliance.events 的测试观察者（真实本地 Broker）
 * <li>全局 BeforeEach 绑定本例专用临时排他队列，使 ClientReviewDecided / QualificationChanged 发布可路由；AfterEach 删除。</li>
 * <li>只观察信封，不发布、不修改补偿任务。</li>
 */
@TestComponent
public class ComplianceEventProbe {
    /** 消息到达的最长等待。 */
    private static final long WAIT_MILLIS = 20_000;

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("compliance");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper json;

    private final List<JsonNode> received = new ArrayList<>();

    private String queue;

    /** 注入 Broker 管理、模板与 JSON。 */
    public ComplianceEventProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, ObjectMapper json) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.json = json;
    }

    /** 声明本例临时队列并绑定 compliance.events。 */
    public void bind() {
        received.clear();
        queue = "compliance_test_events_" + UUID.randomUUID();
        Queue declared = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(declared);
        amqpAdmin.declareBinding(BindingBuilder.bind(declared).to(new FanoutExchange(EVENTS_EXCHANGE, true, false)));
    }

    /** 删除临时队列。 */
    public void unbind() {
        if (queue != null) {
            amqpAdmin.deleteQueue(queue);
            queue = null;
        }
        received.clear();
    }

    /**
     * 等待一条指定类型与聚合 ID 的信封
     *
     * @param eventType 事件类型
     * @param aggregateId 聚合 ID
     * @param predicate 附加条件（例如 outcome），可空
     * @return 信封
     */
    public JsonNode await(String eventType, String aggregateId, java.util.function.Predicate<JsonNode> predicate) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            JsonNode found = find(eventType, aggregateId, predicate);
            if (found != null) {
                return found;
            }
            drain(300);
        }
        return fail("未收到事件 " + eventType + " " + aggregateId);
    }

    /** 已收到的指定类型与聚合 ID 的全部信封。 */
    public List<JsonNode> received(String eventType, String aggregateId) {
        drain(300);
        return received.stream().filter(envelope -> matches(envelope, eventType, aggregateId)).toList();
    }

    /** 没有收到指定类型与聚合 ID 的信封。 */
    public void assertNone(String eventType, String aggregateId) {
        assertTrue(received(eventType, aggregateId).isEmpty(), "不应发布 " + eventType + " " + aggregateId);
    }

    /** 已收到信封中的第一条匹配项。 */
    JsonNode find(String eventType, String aggregateId, java.util.function.Predicate<JsonNode> predicate) {
        return received.stream().filter(envelope -> matches(envelope, eventType, aggregateId))
            .filter(envelope -> predicate == null || predicate.test(envelope)).findFirst().orElse(null);
    }

    /** 拉取队列中的新信封。 */
    void drain(long timeoutMillis) {
        Message message = rabbitTemplate.receive(queue, timeoutMillis);
        while (message != null) {
            received.add(json.readTree(new String(message.getBody(), StandardCharsets.UTF_8)));
            message = rabbitTemplate.receive(queue, 50);
        }
    }

    /** 类型与聚合 ID 匹配。 */
    static boolean matches(JsonNode envelope, String eventType, String aggregateId) {
        return eventType.equals(envelope.path("eventType").asString()) && aggregateId.equals(envelope.path("aggregateId").asString());
    }
}
