package com.paytech.card.test.probe;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测试扮演 Payment 向真实本机 RabbitMQ 的 payment.events 发布 PaymentSucceeded 信封（design/08 §2、design/05 §2.3），由 card 的正式
 * 消费链路（card.payment.hash → card.payment.p0..p3 → 幂等拦截器 → Handler）处理。
 * <li>只清理本服务自己的分区队列与 DLQ（10 §5.3），不触碰其他服务的队列。</li>
 */
@TestComponent
public class PaymentEventPublisher {
    /** 本服务订阅 Payment 的分区数，与测试配置一致。 */
    static final int PARTITIONS = 4;

    private final RabbitDomainEventPublisher publisher;

    private final AmqpAdmin admin;

    /** 以宿主连接、正式编码器与计数器构造 payment 身份的发布器。 */
    public PaymentEventPublisher(ConnectionFactory connectionFactory, AmqpAdmin admin, DomainEventCodec codec,
        MessagingMetrics metrics) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher("payment", Duration.ofSeconds(10), codec, template, metrics);
        this.admin = admin;
    }

    /**
     * 发布一条 PaymentSucceeded
     *
     * @param schemaVersion Payload Schema 版本
     * @param attemptId 支付尝试 ID（聚合 ID）
     * @param attemptVersion 支付尝试版本
     * @param payload Payload
     */
    public void publish(int schemaVersion, long attemptId, long attemptVersion, ObjectNode payload) {
        publisher.publish(DomainEvent.builder().eventType("PaymentSucceeded").schemaVersion(schemaVersion)
            .aggregate("PAYMENT_ATTEMPT", Long.toString(attemptId)).aggregateVersion(attemptVersion)
            .occurredAt(Instant.now().truncatedTo(ChronoUnit.MICROS)).sourceService("payment").instanceId("main")
            .payload(payload).build());
    }

    /**
     * v2 Payload：v1 最小字段（合成值）加 instrumentRef、consentToSave、customerId
     *
     * @param attemptId 支付尝试 ID
     * @param instrumentRef Card 工具引用；非卡支付为 null
     * @param consentToSave 保存意愿
     * @param customerId Customer；访客为 null
     * @return Payload
     */
    public static ObjectNode payload(long attemptId, String instrumentRef, boolean consentToSave, String customerId) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("clientId", "101");
        payload.put("orderId", Long.toString(attemptId));
        payload.put("sourceType", "COMMERCE_ORDER");
        payload.put("paymentIntentId", Long.toString(attemptId + 1));
        payload.put("paymentAttemptId", Long.toString(attemptId));
        payload.put("sourceSubmissionId", Long.toString(attemptId));
        payload.put("entityNo", "AE001");
        payload.put("amount", "10.00");
        payload.put("currency", "AED");
        payload.put("capturedAmount", "10.00");
        payload.put("paymentSucceededTime", Instant.now().truncatedTo(ChronoUnit.MICROS).toString());
        payload.put("paymentMethodCode", "CARD");
        payload.put("channelId", "1");
        payload.put("channelAccountId", "2");
        payload.put("midId", "3");
        payload.put("channelTransactionRef", "T-CARD-" + attemptId);
        payload.put("instrumentRef", instrumentRef);
        payload.put("consentToSave", consentToSave);
        payload.put("customerId", customerId);
        return payload;
    }

    /** 清空本服务订阅 Payment 的分区队列与 DLQ（前例或 payment 测试遗留的消息）。 */
    public void purge() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            admin.purgeQueue(MessagingTopology.partitionQueue("card", "payment", partition), false);
        }
        admin.purgeQueue(MessagingTopology.deadLetterQueue("card", "payment"), false);
    }

    /** 本服务分区队列中尚未投递的消息数。 */
    public int pending() {
        int pending = 0;
        for (int partition = 0; partition < PARTITIONS; partition++) {
            var info = admin.getQueueInfo(MessagingTopology.partitionQueue("card", "payment", partition));
            pending += info == null ? 0 : info.getMessageCount();
        }
        return pending;
    }
}
