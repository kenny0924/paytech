package com.paytech.foundation.messaging.core.topology;

import java.util.Map;
import java.util.Objects;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * 启动时显式声明发布方交换机与订阅方分区拓扑；Broker 不可达或缺少一致性哈希插件时启动失败。
 *
 * @author Claude
 */
public final class MessagingTopologyDeclarer {

    private final AmqpAdmin admin;

    /**
     * 创建声明器。
     *
     * @param admin 宿主连接上的 AmqpAdmin
     */
    public MessagingTopologyDeclarer(AmqpAdmin admin) {
        this.admin = Objects.requireNonNull(admin, "admin must not be null");
    }

    /**
     * 声明本服务作为发布方的交换机。
     *
     * @param producer 本服务名
     */
    public void declareProducer(String producer) {
        admin.declareExchange(new FanoutExchange(MessagingTopology.producerExchange(producer), true, false));
    }

    /**
     * 声明本服务订阅某发布方所需的全部拓扑，可重复执行。
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数，正数
     */
    public void declareSubscription(String consumer, String producer, int partitions) {
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be positive: " + producer);
        }
        FanoutExchange source = new FanoutExchange(MessagingTopology.producerExchange(producer), true, false);
        admin.declareExchange(source);
        CustomExchange hash = declareHashExchange(consumer, producer);
        admin.declareBinding(BindingBuilder.bind(hash).to(source));
        FanoutExchange deadLetter = new FanoutExchange(MessagingTopology.deadLetterExchange(consumer, producer), true,
            false);
        admin.declareExchange(deadLetter);
        Queue deadLetterQueue = QueueBuilder.durable(MessagingTopology.deadLetterQueue(consumer, producer)).quorum()
            .build();
        admin.declareQueue(deadLetterQueue);
        admin.declareBinding(BindingBuilder.bind(deadLetterQueue).to(deadLetter));
        for (int partition = 0; partition < partitions; partition++) {
            Queue queue = partitionQueue(consumer, producer, partition);
            admin.declareQueue(queue);
            admin.declareBinding(new Binding(queue.getName(), Binding.DestinationType.QUEUE, hash.getName(),
                MessagingTopology.PARTITION_WEIGHT, Map.of()));
        }
    }

    private CustomExchange declareHashExchange(String consumer, String producer) {
        CustomExchange hash = new CustomExchange(MessagingTopology.consumerHashExchange(consumer, producer),
            MessagingTopology.CONSISTENT_HASH_TYPE, true, false);
        try {
            admin.declareExchange(hash);
            return hash;
        } catch (AmqpException exception) {
            throw new IllegalStateException("声明一致性哈希交换机失败: " + hash.getName()
                + "；请确认 RabbitMQ 已启用 rabbitmq_consistent_hash_exchange 插件"
                + "（local-environment/rabbitmq/enabled_plugins），再重启应用", exception);
        }
    }

    private static Queue partitionQueue(String consumer, String producer, int partition) {
        return QueueBuilder.durable(MessagingTopology.partitionQueue(consumer, producer, partition))
            .quorum()
            .singleActiveConsumer()
            .deliveryLimit(MessagingTopology.DELIVERY_LIMIT)
            .deadLetterExchange(MessagingTopology.deadLetterExchange(consumer, producer))
            .build();
    }
}
