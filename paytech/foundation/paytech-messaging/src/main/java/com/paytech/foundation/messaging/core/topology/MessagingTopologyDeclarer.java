package com.paytech.foundation.messaging.core.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import com.rabbitmq.client.ShutdownSignalException;

/**
 * 声明发布方交换机与订阅方分区拓扑。
 * <li>启动路径（{@code declare*}）：顺序声明，任一项失败即抛出，使应用启动失败（Broker 不可达、缺少一致性哈希插件或参数冲突）。</li>
 * <li>恢复路径（{@code *Declarations}）：按相同参数生成带依赖关系的声明项，由 {@link MessagingTopologyRecovery} 逐项执行、记录失败并重试。</li>
 *
 * @author Claude
 */
public final class MessagingTopologyDeclarer {

    private final RabbitAdmin admin;

    /**
     * 创建声明器。
     *
     * @param admin 宿主连接上的 RabbitAdmin
     */
    public MessagingTopologyDeclarer(RabbitAdmin admin) {
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
     * 恢复路径的发布方声明：交换机，以及到各订阅方一致性哈希交换机的绑定。
     * <li>删除发布方交换机会同时删除订阅方接在它上面的绑定，而订阅方此时收不到任何信号；因此由发布方按事件目录的订阅方补回绑定。</li>
     * <li>订阅方哈希交换机尚不存在（未部署或尚未声明）时跳过，不算失败；订阅方声明自身拓扑时会建立该绑定。</li>
     *
     * @param producer 本服务名
     * @param subscribers 事件目录中订阅本服务事件的服务名
     * @return 按声明顺序排列的声明项
     */
    List<TopologyDeclaration> producerDeclarations(String producer, Collection<String> subscribers) {
        FanoutExchange exchange = producerExchange(producer);
        String exchangeItem = exchangeItem(exchange.getName());
        List<TopologyDeclaration> plan = new ArrayList<>();
        plan.add(TopologyDeclaration.of(exchangeItem, () -> admin.declareExchange(exchange)));
        for (String subscriber : subscribers) {
            CustomExchange hash = hashExchange(subscriber, producer);
            plan.add(TopologyDeclaration.of(bindingName(exchange.getName(), hash.getName()),
                () -> bindIfPresent(hash, exchange), exchangeItem));
        }
        return plan;
    }

    /**
     * 恢复路径的订阅方声明，参数与启动路径相同；每个绑定依赖其两端。
     * <li>冲突的分区队列不绑定到哈希交换机，避免把消息路由进参数错误的队列；其余分区照常恢复。</li>
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数，正数
     * @return 按声明顺序排列的声明项
     */
    List<TopologyDeclaration> subscriptionDeclarations(String consumer, String producer, int partitions) {
        requirePositive(partitions, producer);
        List<TopologyDeclaration> plan = new ArrayList<>();
        CustomExchange hash = hashExchange(consumer, producer);
        String hashItem = exchangeItem(hash.getName()) + "（需 rabbitmq_consistent_hash_exchange 插件）";
        addHashRoute(plan, producerExchange(producer), hash, hashItem);
        addDeadLetter(plan, deadLetterExchange(consumer, producer), deadLetterQueue(consumer, producer));
        for (int partition = 0; partition < partitions; partition++) {
            Queue queue = partitionQueue(consumer, producer, partition);
            String queueItem = queueItem(queue.getName());
            plan.add(TopologyDeclaration.of(queueItem, () -> admin.declareQueue(queue)));
            plan.add(TopologyDeclaration.of(bindingName(hash.getName(), queue.getName()),
                () -> admin.declareBinding(partitionBinding(queue, hash)), hashItem, queueItem));
        }
        return plan;
    }

    private void addHashRoute(List<TopologyDeclaration> plan, FanoutExchange source, CustomExchange hash,
        String hashItem) {
        String sourceItem = exchangeItem(source.getName());
        plan.add(TopologyDeclaration.of(sourceItem, () -> admin.declareExchange(source)));
        plan.add(TopologyDeclaration.of(hashItem, () -> admin.declareExchange(hash)));
        plan.add(TopologyDeclaration.of(bindingName(source.getName(), hash.getName()),
            () -> admin.declareBinding(BindingBuilder.bind(hash).to(source)), sourceItem, hashItem));
    }

    private void addDeadLetter(List<TopologyDeclaration> plan, FanoutExchange deadLetter, Queue deadLetterQueue) {
        String exchangeItem = exchangeItem(deadLetter.getName());
        String queueItem = queueItem(deadLetterQueue.getName());
        plan.add(TopologyDeclaration.of(exchangeItem, () -> admin.declareExchange(deadLetter)));
        plan.add(TopologyDeclaration.of(queueItem, () -> admin.declareQueue(deadLetterQueue)));
        plan.add(TopologyDeclaration.of(bindingName(deadLetter.getName(), deadLetterQueue.getName()),
            () -> admin.declareBinding(BindingBuilder.bind(deadLetterQueue).to(deadLetter)), exchangeItem, queueItem));
    }

    private void bindIfPresent(CustomExchange hash, FanoutExchange source) {
        if (!exchangeExists(hash.getName())) {
            return;
        }
        admin.declareBinding(BindingBuilder.bind(hash).to(source));
    }

    /** 被动声明判断交换机是否存在；Broker 以 404 关闭通道表示不存在，其余异常照常抛出。 */
    private boolean exchangeExists(String exchange) {
        try {
            admin.getRabbitTemplate().execute(channel -> channel.exchangeDeclarePassive(exchange));
            return true;
        } catch (AmqpException exception) {
            if (isPassiveNotFound(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private static boolean isPassiveNotFound(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal && RabbitUtils.isPassiveDeclarationChannelClose(signal)) {
                return true;
            }
        }
        return false;
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

    private static String exchangeItem(String exchange) {
        return "exchange " + exchange;
    }

    private static String queueItem(String queue) {
        return "queue " + queue;
    }

    private static void requirePositive(int partitions, String producer) {
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be positive: " + producer);
        }
    }
}
