package com.paytech.billing.test.probe;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import com.paytech.billing.service.handler.BillingFactRecordedHandler;
import com.paytech.billing.test.checker.BillingFactEnvelopeChecker;
import com.paytech.billing.test.dal.BillingTestMapper;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * BillingFactRecorded 的测试观察者（真实本地 Broker）
 * <li>bind() 为本例声明一个非持久排他临时队列（带 x-expires）并绑定 paytech-messaging 发布方交换机 billing.events（Fanout），
 * 不消费任何消费方的生产队列。</li>
 * <li>awaitEnvelope() 先等到该事实的 BILLING_FACT_RECORDED 补偿任务被删除（发布已确认），再读取信封并确认只有一条。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class BillingFactEventProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final Duration WAIT = Duration.ofSeconds(15);

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("billing");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final BillingTestMapper mapper;

    private final JsonMapper json;

    private final DomainEventCodec codec;

    private String queue;

    /** 注入 Broker 管理、读取、测试 Mapper 与消费端解码器。 */
    public BillingFactEventProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, BillingTestMapper mapper,
        JsonMapper json, DomainEventCodec codec) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.json = json;
        this.codec = codec;
    }

    /** 声明本例临时队列并绑定 billing.events。 */
    public void bind() {
        queue = "billing_test_billing_events_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
        // 排他队列随连接关闭删除，x-expires 兜底清理进程异常退出后的残留。
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

    /** 等待该事实的发布完成，返回 billing.events 上唯一一条信封的 Checker。 */
    public BillingFactEnvelopeChecker awaitEnvelope(long factId) {
        awaitPublished(factId);
        BillingFactEnvelopeChecker found = null;
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (found == null && System.currentTimeMillis() < deadline) {
            found = next(factId, 500);
        }
        if (found == null) {
            return fail("计费事实信封未到达 billing.events：" + factId);
        }
        assertNull(next(factId, 500), "同一事实收到多条信封：" + factId);
        return found;
    }

    /** 该事实没有补偿任务，临时队列中也没有它的信封。 */
    public void assertNoEnvelope(long factId) {
        assertEquals(List.of(), events(factId), "不应登记计费事实发布任务：" + factId);
        Message message;
        while ((message = rabbitTemplate.receive(queue, 300)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertNotEquals(Long.toString(factId), envelope.path("aggregateId").asString(), "不应发布计费事实：" + factId);
        }
    }

    /** 读取下一条属于该事实的信封；其他事实的消息跳过。 */
    BillingFactEnvelopeChecker next(long factId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(queue, timeoutMillis)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(factId).equals(envelope.path("aggregateId").asString())) {
                return new BillingFactEnvelopeChecker(message, envelope, codec);
            }
        }
        return null;
    }

    /** 补偿任务发布成功后被删除；进入 ERROR / FAILED 说明发布失败。 */
    void awaitPublished(long factId) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(50)).until(() -> {
            List<Map<String, Object>> rows = events(factId);
            if (rows.isEmpty()) {
                return true;
            }
            Object status = rows.getFirst().get("execute_status");
            if ("ERROR".equals(status) || "FAILED".equals(status)) {
                fail("计费事实发布失败：" + factId + " " + status);
            }
            return false;
        });
    }

    /** 该事实的 BILLING_FACT_RECORDED 补偿任务。 */
    public List<Map<String, Object>> events(long factId) {
        return mapper.selectFactCompensationEvents(BillingFactRecordedHandler.EVENT_TYPE, factId);
    }
}
