package com.paytech.foundation.messaging.core.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.messaging.api.ConsumedEventStore;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.catalog.YamlEventCatalog;
import com.paytech.foundation.messaging.consume.DomainEventHandlerRegistry;
import com.paytech.foundation.messaging.consume.DomainEventMessageListener;
import com.paytech.foundation.messaging.consume.IdempotentConsumeInterceptor;
import com.paytech.foundation.messaging.consume.PartitionedListenerContainerFactory;
import com.paytech.foundation.messaging.consume.PartitionedListenerContainers;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.observability.MessagingMetrics;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.messaging.persistence.mapper.ConsumedEventMapper;
import com.paytech.foundation.messaging.persistence.repository.JdbcConsumedEventStore;
import com.paytech.foundation.messaging.publish.RabbitDomainEventPublisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@code paytech-messaging} 自动配置：只在 {@code -app} 引入。
 * <li>发布端：{@code messaging.producer.service} 设置时声明交换机并提供 {@link DomainEventPublisher}；连接必须开启
 * {@code spring.rabbitmq.publisher-confirm-type=correlated} 与 {@code spring.rabbitmq.publisher-returns=true}。</li>
 * <li>消费端：{@code messaging.consumer.service} 设置时按 Handler 声明分区拓扑、创建监听容器，消费存储使用宿主
 * MyBatis DataSource 与事务管理器。</li>
 *
 * @author Claude
 */
@AutoConfiguration(afterName = {"org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
    "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "messaging", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    private static final String MAPPER_RESOURCE = "META-INF/mybatis/messaging/ConsumedEventMapper.xml";

    private static final int MAX_PARTITIONS = 64;

    /**
     * 打包在 Jar 中的事件目录。
     *
     * @return 已校验的目录
     */
    @Bean
    @ConditionalOnMissingBean
    public EventCatalog eventCatalog() {
        return YamlEventCatalog.fromClasspath();
    }

    /**
     * 信封编码。
     *
     * @return 编码器
     */
    @Bean
    @ConditionalOnMissingBean
    public DomainEventCodec domainEventCodec() {
        return new DomainEventCodec();
    }

    /**
     * 消息计数器；宿主没有 MeterRegistry 时使用内存注册表。
     *
     * @param meterRegistry 宿主注册表
     * @return 计数器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessagingMetrics messagingMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new MessagingMetrics(meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    /**
     * 拓扑声明器。
     *
     * @param connectionFactory 宿主连接
     * @return 声明器
     */
    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean
    public MessagingTopologyDeclarer messagingTopologyDeclarer(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(false);
        return new MessagingTopologyDeclarer(admin);
    }

    /**
     * 发布端：校验连接的 Confirm 与 Returns、声明本服务交换机并创建专用 mandatory 模板。
     *
     * @param properties 配置
     * @param connectionFactory 宿主连接
     * @param declarer 拓扑声明器
     * @param codec 信封编码
     * @param metrics 计数器
     * @return 发布端口实现
     */
    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnProperty(prefix = "messaging.producer", name = "service")
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public RabbitDomainEventPublisher domainEventPublisher(MessagingProperties properties,
        ConnectionFactory connectionFactory, MessagingTopologyDeclarer declarer, DomainEventCodec codec,
        MessagingMetrics metrics) {
        String producer = requireService("messaging.producer.service", properties.getProducer().getService());
        if (!connectionFactory.isPublisherConfirms() || !connectionFactory.isPublisherReturns()) {
            throw new IllegalStateException("messaging 发布端要求 spring.rabbitmq.publisher-confirm-type=correlated 且 "
                + "spring.rabbitmq.publisher-returns=true");
        }
        requirePositive("messaging.producer.confirm-timeout", properties.getProducer().getConfirmTimeout());
        declarer.declareProducer(producer);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return new RabbitDomainEventPublisher(producer, properties.getProducer().getConfirmTimeout(), codec, template,
            metrics);
    }

    /**
     * 消费存储的 Mapper：把 XML 加载进宿主 MyBatis 配置。
     *
     * @param sqlSessionFactory 宿主 SqlSessionFactory
     * @return Mapper 工厂
     */
    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    @ConditionalOnProperty(prefix = "messaging.consumer", name = "service")
    @ConditionalOnMissingBean(ConsumedEventMapper.class)
    public MapperFactoryBean<ConsumedEventMapper> consumedEventMapper(SqlSessionFactory sqlSessionFactory) {
        loadMapperXml(sqlSessionFactory.getConfiguration());
        MapperFactoryBean<ConsumedEventMapper> factoryBean = new MapperFactoryBean<>(ConsumedEventMapper.class);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        factoryBean.setAddToConfig(false);
        return factoryBean;
    }

    /**
     * 消费存储。
     *
     * @param mapper Mapper
     * @return 存储
     */
    @Bean
    @ConditionalOnBean(ConsumedEventMapper.class)
    @ConditionalOnMissingBean(ConsumedEventStore.class)
    public JdbcConsumedEventStore consumedEventStore(ConsumedEventMapper mapper) {
        return new JdbcConsumedEventStore(mapper);
    }

    /**
     * 按事件目录核对的 Handler 注册表。
     *
     * @param properties 配置
     * @param consumers 宿主全部消费者
     * @param catalog 事件目录
     * @return 注册表
     */
    @Bean
    @ConditionalOnProperty(prefix = "messaging.consumer", name = "service")
    @ConditionalOnMissingBean
    public DomainEventHandlerRegistry domainEventHandlerRegistry(MessagingProperties properties,
        ObjectProvider<DomainEventConsumer> consumers, EventCatalog catalog) {
        String consumer = requireService("messaging.consumer.service", properties.getConsumer().getService());
        return new DomainEventHandlerRegistry(consumer, consumers.orderedStream().toList(), catalog);
    }

    /**
     * 幂等拦截器，使用宿主事务管理器的 REQUIRED 事务。
     *
     * @param store 消费存储
     * @param transactionManager 宿主事务管理器
     * @return 拦截器
     */
    @Bean
    @ConditionalOnBean({ConsumedEventStore.class, PlatformTransactionManager.class})
    @ConditionalOnMissingBean
    public IdempotentConsumeInterceptor idempotentConsumeInterceptor(ConsumedEventStore store,
        PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return new IdempotentConsumeInterceptor(store, transaction);
    }

    /**
     * 声明每个订阅的分区拓扑，并为每个分区队列创建一个监听容器。
     *
     * @param properties 配置
     * @param connectionFactory 宿主连接
     * @param declarer 拓扑声明器
     * @param registry Handler 注册表
     * @param interceptor 幂等拦截器
     * @param codec 信封编码
     * @param metrics 计数器
     * @return 统一启停的容器集合
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean({ConnectionFactory.class, IdempotentConsumeInterceptor.class, DomainEventHandlerRegistry.class})
    @ConditionalOnMissingBean
    public PartitionedListenerContainers partitionedListenerContainers(MessagingProperties properties,
        ConnectionFactory connectionFactory, MessagingTopologyDeclarer declarer, DomainEventHandlerRegistry registry,
        IdempotentConsumeInterceptor interceptor, DomainEventCodec codec, MessagingMetrics metrics) {
        String consumer = properties.getConsumer().getService();
        requirePositive("messaging.consumer.retry-backoff", properties.getConsumer().getRetryBackoff());
        requireKnownSubscriptions(properties.getConsumers(), registry);
        PartitionedListenerContainerFactory factory = new PartitionedListenerContainerFactory(connectionFactory);
        List<SimpleMessageListenerContainer> containers = new ArrayList<>();
        for (String producer : registry.producers()) {
            int partitions = partitions(properties.getConsumers().get(producer), producer);
            declarer.declareSubscription(consumer, producer, partitions);
            DomainEventMessageListener listener = new DomainEventMessageListener(producer,
                properties.getConsumer().getRetryBackoff(), codec, metrics, registry, interceptor);
            for (int partition = 0; partition < partitions; partition++) {
                containers.add(factory.create(MessagingTopology.partitionQueue(consumer, producer, partition),
                    listener));
            }
        }
        return new PartitionedListenerContainers(containers);
    }

    private static void requireKnownSubscriptions(Map<String, MessagingProperties.Subscription> subscriptions,
        DomainEventHandlerRegistry registry) {
        for (String producer : subscriptions.keySet()) {
            if (!registry.producers().contains(producer)) {
                throw new IllegalStateException("messaging.consumers 配置了本服务未订阅的发布方: " + producer);
            }
        }
    }

    private static int partitions(MessagingProperties.Subscription subscription, String producer) {
        int partitions = subscription == null ? new MessagingProperties.Subscription().getPartitions()
            : subscription.getPartitions();
        if (partitions < 1 || partitions > MAX_PARTITIONS) {
            throw new IllegalStateException("messaging.consumers." + producer + ".partitions 必须在 1.." + MAX_PARTITIONS
                + " 之间");
        }
        return partitions;
    }

    private static String requireService(String key, String value) {
        if (value == null || !com.paytech.foundation.messaging.api.DomainEvent.SERVICE_NAME.matcher(value).matches()) {
            throw new IllegalStateException(key + " 必须是 01 §3 的服务名");
        }
        return value;
    }

    private static void requirePositive(String key, java.time.Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(key + " 必须为正");
        }
    }

    private static void loadMapperXml(Configuration configuration) {
        synchronized (configuration) {
            if (configuration.hasStatement(ConsumedEventMapper.class.getName() + ".insertIfAbsent", false)) {
                return;
            }
            try (var input = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
                new XMLMapperBuilder(input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
            } catch (IOException exception) {
                throw new IllegalStateException("无法加载 messaging MyBatis Mapper: " + MAPPER_RESOURCE, exception);
            }
        }
    }
}
