package com.paytech.commercial.test.probe;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * commercial 领域事件的测试观察者（真实本地 Broker）
 * <li>bind() 为本例声明一个非持久排他临时队列（带 x-expires）并绑定发布方交换机 commercial.events（Fanout），不消费任何消费方队列。</li>
 * <li>awaitEnvelope() 先等到该事件键的补偿任务被删除（发布已确认），再读取信封并确认同一事件键只有一条。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class CommercialEventProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final Duration WAIT = Duration.ofSeconds(15);

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("commercial");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final CommercialTestMapper mapper;

    private final JsonMapper json = JsonMapper.builder().build();

    private String queue;

    /** 注入 Broker 管理、读取与测试 Mapper。 */
    public CommercialEventProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, CommercialTestMapper mapper) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
    }

    /** 声明本例临时队列并绑定 commercial.events。 */
    public void bind() {
        queue = "commercial_test_commercial_events_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者；排他队列随连接关闭删除，x-expires 兜底清理残留。
        Queue temporary = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(temporary);
        amqpAdmin.declareBinding(BindingBuilder.bind(temporary).to(new FanoutExchange(EVENTS_EXCHANGE, true, false)));
    }

    /** 删除临时队列。 */
    public void unbind() {
        if (queue != null) {
            amqpAdmin.deleteQueue(queue);
            queue = null;
        }
    }

    /**
     * 等待该补偿任务发布完成，返回 commercial.events 上该事件键唯一一条信封
     *
     * @param handlerEventType 补偿 Handler 类型
     * @param eventKey 四段式事件键（同时是信封 eventId）
     * @return 信封 JSON
     */
    public JsonNode awaitEnvelope(String handlerEventType, String eventKey) {
        awaitPublished(handlerEventType, eventKey);
        JsonNode found = null;
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (found == null && System.currentTimeMillis() < deadline) {
            found = next(eventKey, 500);
        }
        if (found == null) {
            return fail("事件信封未到达 commercial.events：" + eventKey);
        }
        assertNull(next(eventKey, 500), "同一事件键收到多条信封：" + eventKey);
        return found;
    }

    /**
     * 等待同一 Handler 的多个事件键全部发布，一次读取临时队列收集它们的信封（逐个 awaitEnvelope 会跳过并丢弃后到的其他事件）
     *
     * @param handlerEventType 补偿 Handler 类型
     * @param eventKeys 四段式事件键
     * @return 事件键 → 信封，覆盖全部入参
     */
    public Map<String, JsonNode> awaitEnvelopes(String handlerEventType, String... eventKeys) {
        Map<String, JsonNode> found = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (found.size() < eventKeys.length && System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message == null) {
                continue;
            }
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String eventId = envelope.path("eventId").asString();
            if (List.of(eventKeys).contains(eventId)) {
                assertNull(found.put(eventId, envelope), "同一事件键收到多条信封：" + eventId);
            }
        }
        for (String eventKey : eventKeys) {
            awaitPublished(handlerEventType, eventKey);
            if (!found.containsKey(eventKey)) {
                return fail("事件信封未到达 commercial.events：" + eventKey);
            }
        }
        return found;
    }

    /**
     * 临时队列中没有该事件键的信封
     *
     * @param eventKey 四段式事件键
     */
    public void assertNoEnvelope(String eventKey) {
        assertNull(next(eventKey, 300), "不应发布事件：" + eventKey);
    }

    /** 读取下一条 eventId 等于该事件键的信封；其他消息跳过。 */
    JsonNode next(String eventKey, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(queue, timeoutMillis)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (eventKey.equals(envelope.path("eventId").asString())) {
                return envelope;
            }
        }
        return null;
    }

    /** 补偿任务发布成功后被删除；进入 ERROR / FAILED 说明发布失败。 */
    void awaitPublished(String handlerEventType, String eventKey) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(50)).until(() -> {
            List<Map<String, Object>> rows = mapper.compensationEvents(handlerEventType, eventKey);
            if (rows.isEmpty()) {
                return true;
            }
            Object status = rows.getFirst().get("execute_status");
            if ("ERROR".equals(status) || "FAILED".equals(status)) {
                fail("事件发布失败：" + eventKey + " " + status + " " + rows.getFirst().get("last_error_code"));
            }
            return false;
        });
    }
}
