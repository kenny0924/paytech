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
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.payment.execution.service.handler.PaymentSucceededHandler;
import com.paytech.payment.integration.mq.PaymentResultRabbitPublisher;
import com.paytech.payment.test.checker.PaymentSucceededMessageChecker;
import com.paytech.payment.test.dal.ChannelTestMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PaymentSucceeded 发布的测试观察者
 * <li>bind() 声明本例专用的非持久排他临时队列（带 x-expires），绑定到生产 Exchange topic_payment_result / payment.succeeded.v1；
 * 不消费任何消费方的生产队列。</li>
 * <li>awaitSucceeded() 先等到该尝试的 PAYMENT_SUCCEEDED 补偿任务被删除（Broker 已确认），再从临时队列读取该尝试的消息，并确认只有一条。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class PaymentResultProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final long WAIT_MILLIS = 15_000;

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final ChannelTestMapper mapper;

    private final JsonMapper json;

    private String queue;

    public PaymentResultProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, ChannelTestMapper mapper,
        JsonMapper json) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.json = json;
    }

    /** 声明本例临时队列并绑定到支付结果 Exchange。 */
    public void bind() {
        queue = "payment_test_payment_result_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
        // 排他队列随连接关闭删除，x-expires 兜底清理进程异常退出后的残留。
        Queue temporary = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(temporary);
        Binding binding = BindingBuilder.bind(temporary)
            .to(new TopicExchange(PaymentResultRabbitPublisher.EXCHANGE, true, false))
            .with(PaymentResultRabbitPublisher.SUCCEEDED_ROUTING_KEY);
        amqpAdmin.declareBinding(binding);
    }

    /** 删除临时队列。 */
    public void unbind() {
        if (queue != null) {
            amqpAdmin.deleteQueue(queue);
            queue = null;
        }
    }

    /** 等待该尝试的成功事件发布完成，返回唯一一条消息的 Checker。 */
    public PaymentSucceededMessageChecker awaitSucceeded(long attemptId) {
        awaitPublished(attemptId);
        PaymentSucceededMessageChecker found = null;
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (found == null && System.currentTimeMillis() < deadline) {
            found = next(attemptId, 500);
        }
        if (found == null) {
            return fail("支付成功消息未到达：" + attemptId);
        }
        assertNull(next(attemptId, 500), "同一尝试收到多条支付成功消息：" + attemptId);
        return found;
    }

    /** 该尝试没有成功补偿任务，临时队列中也没有它的消息。 */
    public void assertNoSucceeded(long attemptId) {
        assertTrue(events(attemptId).isEmpty(), "不应登记支付成功补偿任务：" + attemptId);
        Message message;
        while ((message = rabbitTemplate.receive(queue, 300)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertNotEquals(Long.toString(attemptId), envelope.path("sourceId").asString(), "不应发布支付成功：" + attemptId);
        }
    }

    /** 读取下一条属于该尝试的消息；其他尝试的消息跳过。 */
    PaymentSucceededMessageChecker next(long attemptId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(queue, timeoutMillis)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(attemptId).equals(envelope.path("sourceId").asString())) {
                return new PaymentSucceededMessageChecker(message, envelope, json);
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
