package com.paytech.ledger.test.probe;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedEntryPayload;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 测试扮演 account 向真实本机 RabbitMQ 的 account.events 发布 AccountPosted 信封（序号由用例控制），由 ledger 的正式消费链路
 * （ledger.account.hash → ledger.account.p0..p3 → 幂等拦截器 → AccountPostedPostingHandler）处理。
 * <li>Payload JSON 与 account 发布端同序同格式：标量一律为字符串，缺省为 JSON null；聚合 ID 由
 * {@link AccountPostedPayload#aggregateId} 生成，聚合版本即 postingSequence。</li>
 * <li>只清理本服务自己的分区队列与 DLQ，不触碰其他服务的队列。</li>
 */
@TestComponent
public class AccountPostedPublisher {
    /** 本服务名：分区队列前缀。 */
    static final String CONSUMER = "ledger";

    /** 生产方服务名。 */
    static final String PRODUCER = "account";

    /** 本服务订阅 account 的分区数，与测试配置一致。 */
    static final int PARTITIONS = 4;

    private final RabbitDomainEventPublisher publisher;

    private final AmqpAdmin admin;

    /** 以宿主连接、正式编码器与计数器构造 account 身份的发布器。 */
    public AccountPostedPublisher(ConnectionFactory connectionFactory, AmqpAdmin admin, DomainEventCodec codec,
        MessagingMetrics metrics) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template, metrics);
        this.admin = admin;
    }

    /**
     * 发布一条 AccountPosted
     *
     * @param posting 已分配批次主键与序号的入账批次
     * @return 事件 ID（= account 补偿任务的事件键）
     */
    public String publish(AccountPostedPayload posting) {
        DomainEvent event = event(posting);
        publisher.publish(event);
        return event.eventId();
    }

    /** 与 account 发布端相同的 AccountPosted 信封。 */
    public static DomainEvent event(AccountPostedPayload posting) {
        return DomainEvent.builder().eventType(AccountPostedPayload.EVENT_TYPE)
            .schemaVersion(AccountPostedPayload.SCHEMA_VERSION)
            .aggregate(AccountPostedPayload.AGGREGATE_TYPE, aggregateId(posting))
            .aggregateVersion(posting.getPostingSequence()).occurredAt(posting.getPostedTime())
            .sourceService(PRODUCER).instanceId("main").payload(json(posting)).build();
    }

    /** 批次所属过账流的聚合 ID。 */
    public static String aggregateId(AccountPostedPayload posting) {
        return AccountPostedPayload.aggregateId(
            AccountEntityType.valueOf(posting.getEntityType()), posting.getEntityId(),
            posting.getCurrency(), posting.getShardNo());
    }

    /** 清空本服务订阅 account 的分区队列与 DLQ。 */
    public void purge() {
        for (int partition = 0; partition < PARTITIONS; partition++) {
            admin.purgeQueue(MessagingTopology.partitionQueue(CONSUMER, PRODUCER, partition), false);
        }
        admin.purgeQueue(MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER), false);
    }

    /** 本服务分区队列中尚未投递的消息数。 */
    public int pending() {
        int pending = 0;
        for (int partition = 0; partition < PARTITIONS; partition++) {
            var info = admin.getQueueInfo(MessagingTopology.partitionQueue(CONSUMER, PRODUCER, partition));
            pending += info == null ? 0 : (int)info.getMessageCount();
        }
        return pending;
    }

    /** 本服务 DLQ 中的消息数。 */
    public int deadLetters() {
        var info = admin.getQueueInfo(MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER));
        return info == null ? 0 : (int)info.getMessageCount();
    }

    /** AccountPosted Schema v1 Payload：与 account 发布端相同的字段顺序与字符串标量。 */
    public static ObjectNode json(AccountPostedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("postingId", text(payload.getPostingId()));
        node.put("entityType", payload.getEntityType());
        node.put("entityId", payload.getEntityId());
        node.put("currency", payload.getCurrency());
        node.put("shardNo", text(payload.getShardNo()));
        node.put("postingSequence", text(payload.getPostingSequence()));
        node.put("sourceType", payload.getSourceType());
        node.put("postedTime", text(payload.getPostedTime()));
        node.put("factId", text(payload.getFactId()));
        node.put("factVersion", text(payload.getFactVersion()));
        node.put("factType", payload.getFactType());
        node.put("supersedesFactId", text(payload.getSupersedesFactId()));
        node.put("factApplyStatus", payload.getFactApplyStatus());
        node.put("financeEntityId", text(payload.getFinanceEntityId()));
        node.put("movementId", text(payload.getMovementId()));
        node.put("movementSourceType", payload.getMovementSourceType());
        node.put("movementSourceRef", payload.getMovementSourceRef());
        ArrayNode entries = node.putArray("entries");
        for (AccountPostedEntryPayload entry : payload.getEntries()) {
            ObjectNode item = entries.addObject();
            item.put("entryId", text(entry.getEntryId()));
            item.put("lineNo", text(entry.getLineNo()));
            item.put("accountId", text(entry.getAccountId()));
            item.put("accountCode", entry.getAccountCode());
            item.put("ownerType", entry.getOwnerType());
            item.put("ownerId", entry.getOwnerId());
            item.put("balanceBucket", entry.getBalanceBucket());
            item.put("entrySide", entry.getEntrySide());
            item.put("amount", text(entry.getAmount()));
            item.put("factLineNo", text(entry.getFactLineNo()));
            item.put("reversesEntryId", text(entry.getReversesEntryId()));
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
