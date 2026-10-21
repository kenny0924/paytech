package com.paytech.reconciliation.test.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Comparator;
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

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.json.JsonMapper;

/**
 * 四类对账事件的测试观察者（真实本地 Broker，扮演将来的 billing 读取方式，D-08 的验收用临时队列）
 * <li>bind() 为本例声明非持久排他临时队列（带 x-expires）并绑定发布方交换机 reconciliation.events（Fanout）；绑定后事件才可路由
 * （发布端 unroutable-policy: FAIL）。</li>
 * <li>await(n) 读取本例收到的信封，以真实消费端解码器校验信封；{@link #payload} 按 reconciliation-api 载荷类反序列化。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class ReconciliationEventProbe {
    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("reconciliation");

    /** 消息到达的最长等待毫秒数。 */
    private static final long WAIT_MILLIS = 20_000;

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final JsonMapper json;

    private final DomainEventCodec codec;

    private String queue;

    /** 以宿主 AMQP 组件与正式解码器构造。 */
    public ReconciliationEventProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, JsonMapper json,
        DomainEventCodec codec) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.json = json;
        this.codec = codec;
    }

    /** 声明本例临时队列并绑定 reconciliation.events。 */
    public void bind() {
        queue = "reconciliation_test_events_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
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
     * 等待本例收到恰好 count 条对账事件，按事件类型、聚合 ID 排序返回
     *
     * @param count 期望条数
     * @return 已解码的信封
     */
    public List<DomainEvent> await(int count) {
        List<DomainEvent> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (received.size() < count && System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null) {
                received.add(decode(message));
            }
        }
        if (received.size() < count) {
            fail("对账事件只收到 " + received.size() + " 条，期望 " + count);
        }
        assertNull(rabbitTemplate.receive(queue, 500), "收到多于 " + count + " 条对账事件");
        received.sort(Comparator.comparing(DomainEvent::eventType).thenComparing(DomainEvent::aggregateId));
        return received;
    }

    /** 断言本例没有收到任何对账事件（零发布路径）。 */
    public void assertNone() {
        assertNull(rabbitTemplate.receive(queue, 500), "不应发布对账事件");
    }

    /**
     * 按 reconciliation-api 载荷类反序列化信封载荷
     *
     * @param event 信封
     * @param type 载荷类，如 ChannelBatchReconciledPayload
     * @return 载荷
     */
    public <T> T payload(DomainEvent event, Class<T> type) {
        return json.treeToValue(event.payload(), type);
    }

    /** 解码信封并核对来源服务与消息 ID。 */
    DomainEvent decode(Message message) {
        DomainEvent event = codec.decode(message.getBody());
        assertAll(() -> assertEquals("reconciliation", event.sourceService()),
            () -> assertEquals(event.eventId(), message.getMessageProperties().getMessageId()));
        return event;
    }
}
