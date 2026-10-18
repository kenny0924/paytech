package com.paytech.foundation.messaging.consume;

import java.util.Objects;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * 为每个分区队列创建一个监听容器：并发 1、Prefetch 1、手动 Ack、拒绝不自动重新入队。
 * <li>队列本身为单活消费者（SAC），多实例部署时同一分区只有一个实例在消费，保证同聚合串行。</li>
 * <li>队列缺失不视为致命（{@code missingQueuesFatal=false}）：Broker 重建后队列由连接恢复重声明补回，容器按 Spring AMQP 默认间隔
 * （被动声明重试 5 秒、消费者重启 5 秒）持续重试而不是永久停止。启动时拓扑已在容器启动前声明，声明失败仍使启动失败。</li>
 *
 * @author Claude
 */
public final class PartitionedListenerContainerFactory {

    private final ConnectionFactory connectionFactory;

    /**
     * 创建工厂。
     *
     * @param connectionFactory 宿主 RabbitMQ 连接
     */
    public PartitionedListenerContainerFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    /**
     * 创建未启动的容器，由 {@link PartitionedListenerContainers} 统一启停。
     *
     * @param queueName 分区队列名
     * @param listener 该队列的监听器
     * @return 未启动的容器
     */
    public SimpleMessageListenerContainer create(String queueName, ChannelAwareMessageListener listener) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        // 容器不是 Spring Bean，显式命名使消费线程带分区队列名（日志与线程转储可辨认）。
        container.setBeanName(queueName);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setPrefetchCount(1);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setDefaultRequeueRejected(false);
        container.setMissingQueuesFatal(false);
        container.setAutoStartup(false);
        container.setMessageListener(listener);
        container.afterPropertiesSet();
        return container;
    }
}
