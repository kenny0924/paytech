package com.paytech.payment.test.probe;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
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

import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;
import com.paytech.payment.test.checker.PaymentSucceededEnvelopeChecker;
import com.paytech.payment.test.dal.PaymentTestMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PaymentSucceeded 发布的测试观察者（真实本地 Broker）
 * <li>bind() 为本例声明一个非持久排他临时队列（带 x-expires），绑定 paytech-messaging 发布方交换机 payment.events（Fanout）；
 * 不消费任何订阅服务（acquiring 等）的分区队列。</li>
 * <li>awaitEnvelope() 先等到该尝试的 PAYMENT_SUCCEEDED 补偿任务被删除（发布已确认），再读取信封并确认只有一条。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class PaymentResultProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final long WAIT_MILLIS = 15_000;

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("payment");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final PaymentTestMapper mapper;

    private final JsonMapper json;

    private final DomainEventCodec codec;

    private String envelopeQueue;

    public PaymentResultProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, PaymentTestMapper mapper,
        JsonMapper json, DomainEventCodec codec) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.json = json;
        this.codec = codec;
    }

    /** 声明本例临时队列并绑定 payment.events。 */
    public void bind() {
        envelopeQueue = "payment_test_payment_events_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
        // 排他队列随连接关闭删除，x-expires 兜底清理进程异常退出后的残留。
        Queue envelopes = QueueBuilder.nonDurable(envelopeQueue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(envelopes);
        amqpAdmin.declareBinding(BindingBuilder.bind(envelopes).to(new FanoutExchange(EVENTS_EXCHANGE, true, false)));
    }

    /** 删除临时队列。 */
    public void unbind() {
        if (envelopeQueue != null) {
            amqpAdmin.deleteQueue(envelopeQueue);
            envelopeQueue = null;
        }
    }

    /** 等待该尝试的成功事件发布完成，返回 payment.events 上唯一一条信封的 Checker。 */
    public PaymentSucceededEnvelopeChecker awaitEnvelope(long attemptId) {
        awaitPublished(attemptId);
        PaymentSucceededEnvelopeChecker found = null;
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (found == null && System.currentTimeMillis() < deadline) {
            found = nextEnvelope(attemptId, 500);
        }
        if (found == null) {
            return fail("支付成功信封未到达 payment.events：" + attemptId);
        }
        assertNull(nextEnvelope(attemptId, 500), "同一尝试收到多条支付成功信封：" + attemptId);
        return found;
    }

    /** 该尝试没有成功补偿任务，临时队列中也没有它的信封。 */
    public void assertNoSucceeded(long attemptId) {
        assertTrue(events(attemptId).isEmpty(), "不应登记支付成功补偿任务：" + attemptId);
        Message message;
        while ((message = rabbitTemplate.receive(envelopeQueue, 300)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertNotEquals(Long.toString(attemptId), envelope.path("aggregateId").asString(), "不应发布支付成功信封：" + attemptId);
        }
    }

    /** 读取下一条属于该尝试的新信封；其他尝试的消息跳过。 */
    PaymentSucceededEnvelopeChecker nextEnvelope(long attemptId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(envelopeQueue, timeoutMillis)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(attemptId).equals(envelope.path("aggregateId").asString())) {
                return new PaymentSucceededEnvelopeChecker(message, envelope, codec);
            }
        }
        return null;
    }

    /** 补偿任务发布成功后被删除；进入 ERROR/FAILED 说明发布失败。 */
    void awaitPublished(long attemptId) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (true) {
            List<Map<String, Object>> rows = events(attemptId);
            if (rows.isEmpty()) {
                return;
            }
            Object status = rows.getFirst().get("status");
            if ("ERROR".equals(status) || "FAILED".equals(status)) {
                fail("支付成功发布失败：" + attemptId + " " + status);
            }
            if (System.currentTimeMillis() > deadline) {
                fail("支付成功补偿任务未完成：" + attemptId + " " + status);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待支付成功发布被中断");
            }
        }
    }

    /** 该尝试的 PAYMENT_SUCCEEDED 补偿任务。 */
    List<Map<String, Object>> events(long attemptId) {
        return mapper.compensationEvents(PaymentSucceededHandler.EVENT_TYPE, Long.toString(attemptId));
    }
}
