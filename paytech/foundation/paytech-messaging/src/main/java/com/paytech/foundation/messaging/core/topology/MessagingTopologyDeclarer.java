package com.paytech.foundation.messaging.core.topology;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * 声明发布方交换机与订阅方分区拓扑。
 * <li>启动路径（{@code declare*}）：顺序声明，任一项失败即抛出，使应用启动失败（Broker 不可达、缺少一致性哈希插件或参数冲突）。</li>
 * <li>连接恢复路径（{@code redeclare*}）：按相同参数逐项声明，单项失败记 ERROR（含原因）后继续其余项，依赖失败项的绑定跳过，不向连接回调抛出。</li>
 *
 * @author Claude
 */
public final class MessagingTopologyDeclarer {

    private static final Logger log = LoggerFactory.getLogger(MessagingTopologyDeclarer.class);

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
        admin.declareExchange(producerExchange(producer));
    }

    /**
     * 声明本服务订阅某发布方所需的全部拓扑，可重复执行。
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数，正数
     */
    public void declareSubscription(String consumer, String producer, int partitions) {
        requirePositive(partitions, producer);
        FanoutExchange source = producerExchange(producer);
        admin.declareExchange(source);
        CustomExchange hash = declareHashExchange(consumer, producer);
        admin.declareBinding(BindingBuilder.bind(hash).to(source));
        FanoutExchange deadLetter = deadLetterExchange(consumer, producer);
        admin.declareExchange(deadLetter);
        Queue deadLetterQueue = deadLetterQueue(consumer, producer);
        admin.declareQueue(deadLetterQueue);
        admin.declareBinding(BindingBuilder.bind(deadLetterQueue).to(deadLetter));
        for (int partition = 0; partition < partitions; partition++) {
            Queue queue = partitionQueue(consumer, producer, partition);
            admin.declareQueue(queue);
            admin.declareBinding(partitionBinding(queue, hash));
        }
    }

    /**
     * 连接恢复时重声明发布方交换机；失败记 ERROR，不抛出。
     *
     * @param producer 本服务名
     * @return 失败的声明项数（0 或 1）
     */
    public int redeclareProducer(String producer) {
        Attempts attempts = new Attempts();
        FanoutExchange exchange = producerExchange(producer);
        attempts.declare("exchange " + exchange.getName(), () -> admin.declareExchange(exchange));
        return attempts.failures;
    }

    /**
     * 连接恢复时按原参数逐项重声明订阅拓扑；单项失败记 ERROR 并继续，依赖失败项的绑定跳过，不抛出。
     * <li>冲突的分区队列不绑定到哈希交换机，避免把消息路由进参数错误的队列；其余分区照常恢复。</li>
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数，正数
     * @return 失败的声明项数
     */
    public int redeclareSubscription(String consumer, String producer, int partitions) {
        requirePositive(partitions, producer);
        Attempts attempts = new Attempts();
        CustomExchange hash = hashExchange(consumer, producer);
        boolean hashReady = redeclareHashRoute(attempts, producerExchange(producer), hash);
        redeclareDeadLetter(attempts, deadLetterExchange(consumer, producer), deadLetterQueue(consumer, producer));
        for (int partition = 0; partition < partitions; partition++) {
            Queue queue = partitionQueue(consumer, producer, partition);
            boolean queueReady = attempts.declare("queue " + queue.getName(), () -> admin.declareQueue(queue));
            if (queueReady && hashReady) {
                attempts.declare(bindingName(hash.getName(), queue.getName()),
                    () -> admin.declareBinding(partitionBinding(queue, hash)));
            }
        }
        return attempts.failures;
    }

    private boolean redeclareHashRoute(Attempts attempts, FanoutExchange source, CustomExchange hash) {
        boolean sourceReady = attempts.declare("exchange " + source.getName(), () -> admin.declareExchange(source));
        boolean hashReady = attempts.declare("exchange " + hash.getName() + "（需 rabbitmq_consistent_hash_exchange 插件）",
            () -> admin.declareExchange(hash));
        if (sourceReady && hashReady) {
            attempts.declare(bindingName(source.getName(), hash.getName()),
                () -> admin.declareBinding(BindingBuilder.bind(hash).to(source)));
        }
        return hashReady;
    }

    private void redeclareDeadLetter(Attempts attempts, FanoutExchange deadLetter, Queue deadLetterQueue) {
        boolean exchangeReady = attempts.declare("exchange " + deadLetter.getName(),
            () -> admin.declareExchange(deadLetter));
        boolean queueReady = attempts.declare("queue " + deadLetterQueue.getName(),
            () -> admin.declareQueue(deadLetterQueue));
        if (exchangeReady && queueReady) {
            attempts.declare(bindingName(deadLetter.getName(), deadLetterQueue.getName()),
                () -> admin.declareBinding(BindingBuilder.bind(deadLetterQueue).to(deadLetter)));
        }
    }

    private CustomExchange declareHashExchange(String consumer, String producer) {
        CustomExchange hash = hashExchange(consumer, producer);
        try {
            admin.declareExchange(hash);
            return hash;
        } catch (AmqpException exception) {
            throw new IllegalStateException("声明一致性哈希交换机失败: " + hash.getName()
                + "；请确认 RabbitMQ 已启用 rabbitmq_consistent_hash_exchange 插件"
                + "（local-environment/rabbitmq/enabled_plugins），再重启应用", exception);
        }
    }

    private static FanoutExchange producerExchange(String producer) {
        return new FanoutExchange(MessagingTopology.producerExchange(producer), true, false);
    }

    private static CustomExchange hashExchange(String consumer, String producer) {
        return new CustomExchange(MessagingTopology.consumerHashExchange(consumer, producer),
            MessagingTopology.CONSISTENT_HASH_TYPE, true, false);
    }

    private static FanoutExchange deadLetterExchange(String consumer, String producer) {
        return new FanoutExchange(MessagingTopology.deadLetterExchange(consumer, producer), true, false);
    }

    private static Queue deadLetterQueue(String consumer, String producer) {
        return QueueBuilder.durable(MessagingTopology.deadLetterQueue(consumer, producer)).quorum().build();
    }

    private static Queue partitionQueue(String consumer, String producer, int partition) {
        return QueueBuilder.durable(MessagingTopology.partitionQueue(consumer, producer, partition))
            .quorum()
            .singleActiveConsumer()
            .deliveryLimit(MessagingTopology.DELIVERY_LIMIT)
            .deadLetterExchange(MessagingTopology.deadLetterExchange(consumer, producer))
            .build();
    }

    private static Binding partitionBinding(Queue queue, Exchange hash) {
        return new Binding(queue.getName(), Binding.DestinationType.QUEUE, hash.getName(),
            MessagingTopology.PARTITION_WEIGHT, Map.of());
    }

    private static String bindingName(String source, String destination) {
        return "binding " + source + " -> " + destination;
    }

    private static void requirePositive(int partitions, String producer) {
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be positive: " + producer);
        }
    }

    /** 恢复路径的逐项声明：失败记 ERROR 与原因并计数，返回是否成功供依赖项判断。 */
    private static final class Attempts {

        private int failures;

        private boolean declare(String item, Runnable declaration) {
            try {
                declaration.run();
                return true;
            } catch (AmqpException exception) {
                failures++;
                log.error("连接恢复后重声明拓扑失败，依赖该项的绑定已跳过: {}", item, exception);
                return false;
            }
        }
    }
}
