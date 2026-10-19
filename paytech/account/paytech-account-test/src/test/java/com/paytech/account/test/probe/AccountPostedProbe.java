package com.paytech.account.test.probe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AccountPosted 的测试观察者（真实本地 Broker，扮演 ledger 的读取方式）
 * <li>bind() 为本例声明非持久排他临时队列（带 x-expires）并绑定发布方交换机 account.events（Fanout），不消费 ledger 的生产队列。</li>
 * <li>await(n) 读取本例收到的信封，以真实消费端解码器校验信封与 payloadHash，并按 account-api {@link AccountPostedPayload} 反序列化。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class AccountPostedProbe {
    /** 消息到达的最长等待毫秒数。 */
    private static final long WAIT_MILLIS = 20_000;

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("account");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final JsonMapper json;

    private final DomainEventCodec codec;

    private String queue;

    public AccountPostedProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, JsonMapper json,
        DomainEventCodec codec) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.json = json;
        this.codec = codec;
    }

    /** 声明本例临时队列并绑定 account.events。 */
    public void bind() {
        queue = "account_test_account_events_" + UUID.randomUUID();
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
     * 等待本例收到恰好 count 条 AccountPosted，按聚合 ID、聚合版本排序返回
     *
     * @param count 期望条数
     * @return 已解码的信封
     */
    public List<Posted> await(int count) {
        List<Posted> received = new ArrayList<>();
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (received.size() < count && System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null) {
                received.add(decode(message));
            }
        }
        if (received.size() < count) {
            fail("AccountPosted 只收到 " + received.size() + " 条，期望 " + count);
        }
        Message extra = rabbitTemplate.receive(queue, 500);
        assertTrue(extra == null, "收到多于 " + count + " 条 AccountPosted");
        received.sort(Comparator.comparing((Posted posted) -> posted.event().aggregateId())
            .thenComparingLong(posted -> posted.event().aggregateVersion()));
        return received;
    }

    /** 断言一组信封属于同一流，且版本从 1 起连续。 */
    public static void assertContiguous(List<Posted> posted, String aggregateId) {
        for (int index = 0; index < posted.size(); index++) {
            DomainEvent event = posted.get(index).event();
            long version = index + 1L;
            assertAll(() -> assertEquals(aggregateId, event.aggregateId()),
                () -> assertEquals(version, event.aggregateVersion()),
                () -> assertEquals(AggregateKey.of(AccountPostedPayload.AGGREGATE_TYPE, aggregateId)
                    .eventKey(AccountPostedPayload.EVENT_TYPE, version), event.eventId()));
        }
    }

    /** 解码信封并核对信封与载荷一致。 */
    Posted decode(Message message) {
        DomainEvent event = codec.decode(message.getBody());
        AccountPostedPayload payload = json.treeToValue(event.payload(), AccountPostedPayload.class);
        assertAll(() -> assertEquals(AccountPostedPayload.EVENT_TYPE, event.eventType()),
            () -> assertEquals(AccountPostedPayload.SCHEMA_VERSION, event.schemaVersion()),
            () -> assertEquals(AccountPostedPayload.AGGREGATE_TYPE, event.aggregateType()),
            () -> assertEquals("account", event.sourceService()),
            () -> assertEquals(payload.getPostingSequence(), event.aggregateVersion()),
            () -> assertEquals(payload.getPostedTime(), event.occurredAt()),
            () -> assertEquals(event.eventId(), message.getMessageProperties().getMessageId()));
        return new Posted(event, payload);
    }

    /**
     * 一条已解码的 AccountPosted
     *
     * @param event 信封
     * @param payload 载荷
     */
    public record Posted(DomainEvent event, AccountPostedPayload payload) {

        /** 载荷的 JSON 原文，用于断言字段顺序与字符串标量。 */
        public JsonNode json() {
            return event.payload();
        }
    }
}
