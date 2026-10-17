package com.paytech.compliance.test.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.ext.mq.ClientReviewResultRabbitPublisher;
import com.paytech.compliance.test.checker.ClientReviewDecidedEnvelopeChecker;
import com.paytech.compliance.test.checker.ClientReviewResultChecker;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper;
import com.paytech.compliance.test.mapper.ClientReviewTestMapper.EventRow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 审核结果 MQ 双写的测试观察者（真实本地 Broker）
 * <li>bind() 声明两个本例专用的临时排他队列（带 x-expires）：一个绑定旧 Exchange topic_risk_client_review / client_review.decided.v1，
 * 一个绑定 paytech-messaging 发布方交换机 compliance.events（Fanout）；不消费 client-master 的生产队列 cg_client_master_client_review。</li>
 * <li>awaitResult() 先等到该主任务的 CLIENT_REVIEW_RESULT 补偿任务被删除（两次发布均已确认），再从临时队列读取该任务的旧报文；
 * awaitEnvelope() 读取该任务的新信封。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ClientReviewResultProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final long WAIT_MILLIS = 15_000;

    /** paytech-messaging 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("compliance");

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final ClientReviewTestMapper mapper;

    private final ObjectMapper json;

    private final DomainEventCodec codec;

    private String queue;

    private String envelopeQueue;

    public ClientReviewResultProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, ClientReviewTestMapper mapper,
        ObjectMapper json, DomainEventCodec codec) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.json = json;
        this.codec = codec;
    }

    /** 声明本例两个临时队列并分别绑定旧审核结果 Exchange 与 compliance.events。 */
    public void bind() {
        queue = "compliance_test_client_review_" + UUID.randomUUID();
        envelopeQueue = "compliance_test_compliance_events_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
        // 排他队列随连接关闭删除，x-expires 兜底清理进程异常退出后的残留。
        Queue temporary = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(temporary);
        Binding binding = BindingBuilder.bind(temporary)
            .to(new TopicExchange(ClientReviewResultRabbitPublisher.EXCHANGE, true, false))
            .with(ClientReviewResultRabbitPublisher.ROUTING_KEY);
        amqpAdmin.declareBinding(binding);
        Queue envelopes = QueueBuilder.nonDurable(envelopeQueue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(envelopes);
        amqpAdmin.declareBinding(BindingBuilder.bind(envelopes).to(new FanoutExchange(EVENTS_EXCHANGE, true, false)));
    }

    /** 删除两个临时队列。 */
    public void unbind() {
        if (queue != null) {
            amqpAdmin.deleteQueue(queue);
            queue = null;
        }
        if (envelopeQueue != null) {
            amqpAdmin.deleteQueue(envelopeQueue);
            envelopeQueue = null;
        }
    }

    /** 等待该主任务的结果发布完成并返回 compliance.events 上唯一一条新信封的 Checker。 */
    public ClientReviewDecidedEnvelopeChecker awaitEnvelope(long caseId) {
        awaitPublished(caseId);
        ClientReviewDecidedEnvelopeChecker found = null;
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (found == null && System.currentTimeMillis() < deadline) {
            found = nextEnvelope(caseId, 500);
        }
        if (found == null) {
            return fail("审核结果信封未到达 compliance.events：" + caseId);
        }
        assertTrue(nextEnvelope(caseId, 500) == null, "同一主任务收到多条审核结果信封：" + caseId);
        return found;
    }

    /** 读取下一条属于该主任务的新信封；其他任务的信封跳过。 */
    ClientReviewDecidedEnvelopeChecker nextEnvelope(long caseId, long timeoutMillis) {
        Message message;
        while ((message = rabbitTemplate.receive(envelopeQueue, timeoutMillis)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(caseId).equals(envelope.path("aggregateId").asString())) {
                return new ClientReviewDecidedEnvelopeChecker(message, envelope, codec);
            }
        }
        return null;
    }

    /** 等待该主任务的结果发布完成并返回其消息 Checker。 */
    public ClientReviewResultChecker awaitResult(long caseId) {
        awaitPublished(caseId);
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message == null) {
                continue;
            }
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(caseId).equals(envelope.path("sourceId").asString())) {
                return new ClientReviewResultChecker(message, envelope, json);
            }
        }
        return fail("审核结果消息未到达：" + caseId);
    }

    /** 该主任务没有结果补偿任务，临时队列中也没有它的消息。 */
    public void assertNoResult(long caseId) {
        assertTrue(mapper.resultEvents(caseId).isEmpty(), "不应登记审核结果补偿任务：" + caseId);
        Message message;
        while ((message = rabbitTemplate.receive(queue, 300)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertTrue(!Long.toString(caseId).equals(envelope.path("sourceId").asString()), "不应发布审核结果：" + caseId);
        }
        while ((message = rabbitTemplate.receive(envelopeQueue, 300)) != null) {
            JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            assertTrue(!Long.toString(caseId).equals(envelope.path("aggregateId").asString()), "不应发布审核结果信封：" + caseId);
        }
    }

    /** 补偿任务发布成功后被删除；进入 ERROR/FAILED 说明发布失败。 */
    void awaitPublished(long caseId) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (true) {
            List<EventRow> rows = mapper.resultEvents(caseId);
            if (rows.isEmpty()) {
                return;
            }
            EventRow row = rows.getFirst();
            if ("ERROR".equals(row.executeStatus()) || "FAILED".equals(row.executeStatus())) {
                fail("审核结果发布失败：" + caseId + " " + row.executeStatus());
            }
            if (System.currentTimeMillis() > deadline) {
                fail("审核结果补偿任务未完成：" + caseId + " " + row.executeStatus());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待审核结果发布被中断");
            }
        }
    }
}
