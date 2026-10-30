package com.paytech.tax.test.probe;

import java.time.Duration;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

/**
 * 测试扮演 acquiring 向真实本机 RabbitMQ 的 acquiring.events 发布 OrderPaymentSucceeded 信封，由 tax 的正式消费链路
 * （tax.acquiring.hash → tax.acquiring.p0..p3 → 幂等拦截器 → Handler）处理
 * <li>只清理与统计本服务自己的分区队列与 DLQ，不触碰其他服务（billing 等）的队列。</li>
 */
@TestComponent
public class AcquiringEventPublisher {
    /** 本服务名：分区队列前缀。 */
    static final String CONSUMER = "tax";

    /** 发布方服务名。 */
    static final String PRODUCER = "acquiring";

    /** 本服务订阅 acquiring 的分区数，与测试配置一致。 */
    static final int PARTITIONS = 4;

    private final RabbitDomainEventPublisher publisher;

    private final AmqpAdmin admin;

    /** 以宿主连接、正式编码器与计数器构造 acquiring 身份的发布器。 */
    public AcquiringEventPublisher(ConnectionFactory connectionFactory, AmqpAdmin admin, DomainEventCodec codec,
        MessagingMetrics metrics) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        this.publisher = new RabbitDomainEventPublisher(PRODUCER, Duration.ofSeconds(10), codec, template, metrics);
        this.admin = admin;
    }

    /**
     * 发布一条信封
     *
     * @param event 发布方为 acquiring 的信封
     * @return 事件 ID
     */
    public String publish(DomainEvent event) {
        publisher.publish(event);
        return event.eventId();
    }

    /** 清空本服务订阅 acquiring 的分区队列与 DLQ。 */
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

    /** 本服务 acquiring DLQ 中的消息数。 */
    public int deadLetters() {
        var info = admin.getQueueInfo(MessagingTopology.deadLetterQueue(CONSUMER, PRODUCER));
        return info == null ? 0 : (int)info.getMessageCount();
    }
}
