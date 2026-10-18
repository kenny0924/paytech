package com.paytech.account.test.probe;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测试扮演 billing 向真实本机 RabbitMQ 的 billing.events 发布 EconomicFactRecorded 信封（第 11a 步 Payload 合同），由 account 的正式消费
 * 链路（account.billing.hash → account.billing.p0..p3 → 幂等拦截器 → Handler）处理。
 * <li>Payload JSON 与 billing 的 {@code EconomicFactPayloads.json} 同序同格式：标量一律为字符串，缺省为 JSON null。</li>
 * <li>只清理本服务自己的分区队列与 DLQ（10 §5.3），不触碰其他服务的队列。</li>
 */
@TestComponent
public class BillingEventPublisher {
    /** 本服务名：分区队列前缀。 */
    static final String CONSUMER = "account";

    /** 本服务订阅 billing 的分区数，与测试配置一致。 */
    static final int PARTITIONS = 4;

    private final RabbitDomainEventPublisher publisher;

    private final AmqpAdmin admin;

    /** 以宿主连接、正式编码器与计数器构造 billing 身份的发布器。 */
    public BillingEventPublisher(ConnectionFactory connectionFactory, AmqpAdmin admin, DomainEventCodec codec,
        MessagingMetrics metrics) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher("billing", Duration.ofSeconds(10), codec, template, metrics);
        this.admin = admin;
    }

    /**
     * 发布一条 EconomicFactRecorded
     *
     * @param fact 已分配 factId 与 factVersion 的经济事实
     * @return 事件 ID（= billing 补偿任务的事件键）
     */
    public String publish(EconomicFactPayload fact) {
        DomainEvent event = DomainEvent.builder().eventType(EconomicFactPayload.EVENT_TYPE)
            .schemaVersion(EconomicFactPayload.SCHEMA_VERSION)
            .aggregate(EconomicFactPayload.AGGREGATE_TYPE, fact.getFactId().toString())
            .aggregateVersion(fact.getFactVersion())
            .occurredAt(fact.getRecordedTime() == null ? Instant.now().truncatedTo(ChronoUnit.MICROS) : fact.getRecordedTime())
            .sourceService("billing").instanceId("main").payload(json(fact)).build();
        publisher.publish(event);
        return event.eventId();
    }

    /** 清空本服务订阅 billing 的分区队列与 DLQ。 */
    public void purge() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            admin.purgeQueue(MessagingTopology.partitionQueue(CONSUMER, "billing", partition), false);
        }
        admin.purgeQueue(MessagingTopology.deadLetterQueue(CONSUMER, "billing"), false);
    }

    /** 本服务分区队列中尚未投递的消息数。 */
    public int pending() {
        int pending = 0;
        for (int partition = 0; partition < PARTITIONS; partition++) {
            var info = admin.getQueueInfo(MessagingTopology.partitionQueue(CONSUMER, "billing", partition));
            pending += info == null ? 0 : (int)info.getMessageCount();
        }
        return pending;
    }

    /** 本服务 DLQ 中的消息数。 */
    public int deadLetters() {
        var info = admin.getQueueInfo(MessagingTopology.deadLetterQueue(CONSUMER, "billing"));
        return info == null ? 0 : (int)info.getMessageCount();
    }

    /** EconomicFactRecorded Schema v1 Payload：与 billing 发布端相同的字段顺序与字符串标量。 */
    public static ObjectNode json(EconomicFactPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("factId", text(payload.getFactId()));
        node.put("factVersion", text(payload.getFactVersion()));
        node.put("supersedesFactId", text(payload.getSupersedesFactId()));
        node.put("parentFactId", text(payload.getParentFactId()));
        node.put("factType", payload.getFactType());
        node.put("sourceSystem", payload.getSourceSystem());
        node.put("sourceEventKey", payload.getSourceEventKey());
        node.put("sourceVersion", text(payload.getSourceVersion()));
        node.put("sourcePayloadHash", payload.getSourcePayloadHash());
        node.put("recordedTime", text(payload.getRecordedTime()));
        node.put("paymentSucceededTime", text(payload.getPaymentSucceededTime()));
        node.put("clientId", text(payload.getClientId()));
        node.put("entityNo", payload.getEntityNo());
        node.put("localPartnerId", text(payload.getLocalPartnerId()));
        node.put("partnerAgreementOeId", text(payload.getPartnerAgreementOeId()));
        node.put("financeEntityId", text(payload.getFinanceEntityId()));
        node.put("currency", payload.getCurrency());
        node.put("grossAmount", text(payload.getGrossAmount()));
        node.put("channelAccountId", text(payload.getChannelAccountId()));
        ArrayNode lines = node.putArray("lines");
        for (EconomicFactLinePayload line : payload.getLines()) {
            ObjectNode item = lines.addObject();
            item.put("lineNo", text(line.getLineNo()));
            item.put("componentType", line.getComponentType());
            item.put("partyType", line.getPartyType());
            item.put("partyId", line.getPartyId());
            item.put("payerRole", line.getPayerRole());
            item.put("payerId", line.getPayerId());
            item.put("payeeRole", line.getPayeeRole());
            item.put("payeeId", line.getPayeeId());
            item.put("direction", line.getDirection());
            item.put("postedAmount", text(line.getPostedAmount()));
        }
        return node;
    }

    /** 十进制或时间文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value == null ? null : value.toString();
    }
}
