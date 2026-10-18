package com.paytech.foundation.messaging.testkit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.messaging.consume.DomainEventHandlerRegistry;
import com.paytech.foundation.messaging.core.autoconfigure.MessagingProperties;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;

/**
 * 消费方测试配置以 {@code @Import} 注册 {@link PartitionQueueBaseline}。
 * <li>订阅集合取自宿主实际装配的 Handler 注册表与 {@code messaging.consumers.<producer>.partitions}，与监听容器消费的队列一致。</li>
 *
 * @author Claude
 */
@TestConfiguration(proxyBeanMethods = false)
public class PartitionQueueBaselineConfiguration {

    /** 未配置分区数时的默认值，与 {@link MessagingProperties.Subscription} 一致。 */
    private static final int DEFAULT_PARTITIONS = new MessagingProperties.Subscription().getPartitions();

    /**
     * 本服务全部订阅的分区队列基线。
     *
     * @param properties 宿主 messaging 配置
     * @param registry 宿主 Handler 注册表
     * @param declarer 生产拓扑声明器
     * @param connectionFactory 宿主连接
     * @return 基线组件
     */
    @Bean
    PartitionQueueBaseline partitionQueueBaseline(MessagingProperties properties, DomainEventHandlerRegistry registry,
        MessagingTopologyDeclarer declarer, ConnectionFactory connectionFactory) {
        Map<String, Integer> partitionsByProducer = new LinkedHashMap<>();
        for (String producer : registry.producers()) {
            MessagingProperties.Subscription subscription = properties.getConsumers().get(producer);
            partitionsByProducer.put(producer, subscription == null ? DEFAULT_PARTITIONS : subscription.getPartitions());
        }
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(false);
        return new PartitionQueueBaseline(admin, declarer, properties.getConsumer().getService(),
            partitionsByProducer);
    }
}
