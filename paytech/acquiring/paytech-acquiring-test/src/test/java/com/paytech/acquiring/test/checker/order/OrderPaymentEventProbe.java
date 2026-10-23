package com.paytech.acquiring.test.checker.order;

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
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.test.dal.OrderPaymentEventTestMapper;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OrderPaymentSucceeded 的测试观察者（真实本地 Broker）
 * <li>bind() 为本例声明一个非持久排他临时队列（带 x-expires）并绑定发布方交换机 acquiring.events（Fanout），不消费任何生产队列。</li>
 * <li>awaitEnvelope() 先等到该订单的 ORDER_PAYMENT_SUCCEEDED 补偿任务被删除（发布已确认），再读取信封并确认只有一条。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 */
@TestComponent
public class OrderPaymentEventProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final Duration WAIT = Duration.ofSeconds(15);

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("acquiring");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final DomainEventCodec codec;
    private final OrderPaymentEventTestMapper mapper;
    private String queue;

    /** 读取使用独立 RabbitTemplate，不复用业务发布端。 */
    public OrderPaymentEventProbe(AmqpAdmin amqpAdmin, ConnectionFactory connectionFactory, DomainEventCodec codec,
        OrderPaymentEventTestMapper mapper) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = new RabbitTemplate(connectionFactory);
        this.codec = codec;
        this.mapper = mapper;
    }

    /** 声明本例临时队列并绑定 acquiring.events。 */
    public void bind() {
        queue = "acquiring_test_acquiring_events_" + UUID.randomUUID();
        // 不设 auto-delete：receive 每次轮询都会注销消费者；排他队列随连接关闭删除，x-expires 兜底清理异常退出残留。
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

    /** 等待该订单的发布完成，返回 acquiring.events 上唯一一条信封的 Checker。 */
    public OrderPaymentEnvelopeChecker awaitEnvelope(long orderId) {
        awaitPublished(orderId);
        OrderPaymentEnvelopeChecker found = null;
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (found == null && System.currentTimeMillis() < deadline) {
            found = next(orderId, 500);
        }
        if (found == null) {
            return fail("订单支付成功信封未到达 acquiring.events：" + orderId);
        }
        assertNull(next(orderId, 500), "同一订单收到多条信封：" + orderId);
        return found;
    }

    /** 该订单没有发布任务，临时队列中也没有它的信封。 */
    public void assertNoEnvelope(long orderId) {
        assertEquals(List.of(), tasks(orderId), "不应登记订单支付成功发布任务：" + orderId);
        Message message;
        while ((message = rabbitTemplate.receive(queue, 300)) != null) {
            JsonNode envelope = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertNotEquals(Long.toString(orderId), envelope.path("aggregateId").asString(), "不应发布订单：" + orderId);
        }
    }

    /** 等待该订单的发布任务进入终态失败，返回错误代码；期间不得发布信封。 */
    public String awaitTerminalFailure(long orderId) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(50)).until(() -> {
            List<Map<String, Object>> rows = tasks(orderId);
            return !rows.isEmpty()
                && CompensationEventStatus.FAILED.getCode().equals(rows.getFirst().get("execute_status"));
        });
        assertNull(next(orderId, 300), "终态失败不应发布信封：" + orderId);
        return (String)tasks(orderId).getFirst().get("last_error_code");
    }

    /** 该订单的 ORDER_PAYMENT_SUCCEEDED 补偿任务。 */
    public List<Map<String, Object>> tasks(long orderId) {
        return mapper.tasks(orderId);
    }

    /** 读取下一条属于该订单的信封；其他订单的消息跳过。 */
    OrderPaymentEnvelopeChecker next(long orderId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(queue, timeoutMillis)) != null) {
            JsonNode envelope = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(orderId).equals(envelope.path("aggregateId").asString())) {
                return new OrderPaymentEnvelopeChecker(codec.decode(message.getBody()), envelope);
            }
        }
        return null;
    }

    /** 补偿任务发布成功后被删除；进入 ERROR / FAILED 说明发布失败。 */
    void awaitPublished(long orderId) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(50)).until(() -> {
            List<Map<String, Object>> rows = tasks(orderId);
            if (rows.isEmpty()) {
                return true;
            }
            Object status = rows.getFirst().get("execute_status");
            if (CompensationEventStatus.ERROR.getCode().equals(status)
                || CompensationEventStatus.FAILED.getCode().equals(status)) {
                fail("订单支付成功发布失败：" + orderId + " " + status + " " + rows.getFirst().get("last_error_code"));
            }
            return false;
        });
    }
}
