package com.paytech.channel.connector.test.probe;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.channel.connector.service.handler.ChannelPaymentNotifiedHandler;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import static org.awaitility.Awaitility.await;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ChannelPaymentNotified 发布的测试观察者（真实本地 Broker）
 * <li>bind() 为本例声明非持久排他临时队列（x-expires 兜底），绑定发布方交换机 channel-connector.events（Fanout）；不消费订阅服务的分区队列。</li>
 * <li>awaitEnvelope() 先等到 CHANNEL_PAYMENT_NOTIFIED 补偿任务被删除（发布已确认），再读取该回调的唯一信封。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class ChannelEventProbe {
    /** 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("channel-connector");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final ChannelTestMapper mapper;
    private String queue;

    public ChannelEventProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, ChannelTestMapper mapper) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
    }

    /** 声明本例临时队列并绑定 channel-connector.events。 */
    public void bind() {
        queue = "channel_connector_test_events_" + UUID.randomUUID();
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
    }

    /** 等待发布任务全部完成（成功后删除；ERROR / FAILED 立即失败）。 */
    public void awaitPublished() {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> {
            var rows = mapper.compensationEvents(ChannelPaymentNotifiedHandler.EVENT_TYPE);
            for (var row : rows) {
                if ("ERROR".equals(row.get("status")) || "FAILED".equals(row.get("status"))) {
                    fail("渠道通知发布失败：" + row);
                }
            }
            return rows.isEmpty();
        });
    }

    /** 等待发布完成并返回该回调唯一的信封；没有或多于一条时失败。 */
    public JsonNode awaitEnvelope(long callbackId) {
        awaitPublished();
        JsonNode found = next(callbackId, 5_000);
        if (found == null) {
            return fail("渠道通知信封未到达 " + EVENTS_EXCHANGE + "：" + callbackId);
        }
        assertNull(next(callbackId, 300), "同一回调收到多条信封：" + callbackId);
        return found;
    }

    /** 临时队列中没有任何信封。 */
    public void assertNoEnvelope() {
        Message message = rabbitTemplate.receive(queue, 300);
        assertNull(message, "不应发布渠道通知");
    }

    /** 读取下一条属于该回调的信封；其他回调的消息跳过。 */
    JsonNode next(long callbackId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(queue, timeoutMillis)) != null) {
            JsonNode envelope = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(callbackId).equals(envelope.path("aggregateId").asString())) {
                return envelope;
            }
        }
        return null;
    }
}
