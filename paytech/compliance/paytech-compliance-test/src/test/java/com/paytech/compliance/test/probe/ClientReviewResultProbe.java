package com.paytech.risk.test.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

import com.paytech.risk.ext.mq.ClientReviewResultRabbitPublisher;
import com.paytech.risk.test.checker.compliance.ClientReviewResultChecker;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper;
import com.paytech.risk.test.mapper.compliance.ClientReviewTestMapper.EventRow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 审核结果 MQ 发布的测试观察者
 * <li>bind() 声明一个本例专用的临时排他队列（带 x-expires），绑定到生产 Exchange topic_risk_client_review / client_review.decided.v1；
 * 不消费 CCT 的生产队列 cg_cct_client_review。</li>
 * <li>awaitResult() 先等到该主任务的 CLIENT_REVIEW_RESULT 补偿任务被删除（Broker 已确认），再从临时队列读取该任务的消息。</li>
 * <li>只观察，不发布、不修改补偿任务；unbind() 删除临时队列。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ClientReviewResultProbe {
    /** 补偿任务完成与消息到达的最长等待时间。 */
    private static final long WAIT_MILLIS = 15_000;

    private final AmqpAdmin amqpAdmin;

    private final RabbitTemplate rabbitTemplate;

    private final ClientReviewTestMapper mapper;

    private final ObjectMapper json;

    private String queue;

    public ClientReviewResultProbe(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, ClientReviewTestMapper mapper,
        ObjectMapper json) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.json = json;
    }

    /** 声明本例临时队列并绑定到审核结果 Exchange。 */
    public void bind() {
        queue = "risk_test_client_review_" + UUID.randomUUID();
        // 不设 auto-delete：RabbitTemplate.receive 每次轮询都会注销消费者，auto-delete 队列会在第一次轮询后被 Broker 删除。
        // 排他队列随连接关闭删除，x-expires 兜底清理进程异常退出后的残留。
        Queue temporary = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        amqpAdmin.declareQueue(temporary);
        Binding binding = BindingBuilder.bind(temporary)
            .to(new TopicExchange(ClientReviewResultRabbitPublisher.EXCHANGE, true, false))
            .with(ClientReviewResultRabbitPublisher.ROUTING_KEY);
        amqpAdmin.declareBinding(binding);
    }

    /** 删除临时队列。 */
    public void unbind() {
        if (queue != null) {
            amqpAdmin.deleteQueue(queue);
            queue = null;
        }
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
