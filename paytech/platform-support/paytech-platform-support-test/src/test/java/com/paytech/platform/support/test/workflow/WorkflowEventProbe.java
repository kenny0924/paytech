package com.paytech.platform.support.test.workflow;

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

import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.testsupport.api.Clear;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 本例临时队列绑定 platform-support.events，观察工作流回调信封；只读，不消费业务队列。 */
@TestComponent
public class WorkflowEventProbe implements Clear {

    /** 最长等待。 */
    private static final Duration WAIT = Duration.ofSeconds(15);

    /** Platform 发布方交换机。 */
    public static final String EVENTS_EXCHANGE = MessagingTopology.producerExchange("platform-support");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AmqpAdmin admin;
    private final RabbitTemplate template;
    private String queue;

    /**
     * 注入 Broker 管理与读取。
     *
     * @param admin Broker 管理
     * @param template 读取
     */
    public WorkflowEventProbe(AmqpAdmin admin, RabbitTemplate template) {
        this.admin = admin;
        this.template = template;
    }

    /** 声明本例临时排他队列并绑定。 */
    public void bind() {
        queue = "platform_test_workflow_events_" + UUID.randomUUID();
        Queue temporary = QueueBuilder.nonDurable(queue).exclusive().expires(600_000).build();
        admin.declareQueue(temporary);
        admin.declareBinding(BindingBuilder.bind(temporary).to(new FanoutExchange(EVENTS_EXCHANGE, true, false)));
    }

    /**
     * 等待某实例的下一条信封。
     *
     * @param instanceId 实例 ID
     * @return 信封 JSON
     */
    public JsonNode awaitEnvelope(long instanceId) {
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Message message = template.receive(queue, 500);
            if (message == null) {
                continue;
            }
            JsonNode envelope = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (Long.toString(instanceId).equals(envelope.path("aggregateId").asString())) {
                return envelope;
            }
        }
        return fail("工作流回调信封未到达 " + EVENTS_EXCHANGE + "：" + instanceId);
    }

    /** 删除临时队列。 */
    @Override
    public void clear() {
        if (queue != null) {
            admin.deleteQueue(queue);
            queue = null;
        }
    }
}
