package com.paytech.platform.support.auto.configuration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.paytech.foundation.messaging.api.ConsumedEventRepository;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.consume.DomainEventHandlerRegistry;
import com.paytech.foundation.messaging.consume.PartitionedListenerContainers;
import com.paytech.foundation.messaging.core.autoconfigure.MessagingAutoConfiguration;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.platform.support.test.base.MockTestBase;

/**
 * paytech-messaging 在 Platform 正式应用中的装配：编解码与事件目录可用；工作流业务回调启用发布端
 * （producer.service=platform-support，连接本地 RabbitMQ 并声明发布方交换机）；未设置 consumer.service，
 * 不存在消费注册表、消费记录与监听容器（Platform 不消费事件）。
 *
 * @see MessagingAutoConfiguration#eventCatalog()
 */
class MessagingAssemblyTest extends MockTestBase {

    @Autowired
    ApplicationContext context;

    /** 装配发布端，不装配消费端。 */
    @Test
    void test_normalFlow() {
        assertAll(() -> assertEquals(1, context.getBeansOfType(EventCatalog.class).size()),
            () -> assertEquals(1, context.getBeansOfType(DomainEventCodec.class).size()),
            () -> assertTrue(context.getBean(EventCatalog.class).entries().size() > 0, "事件目录随 Jar 打包"),
            () -> assertTrue(hasBean(ConnectionFactory.class), "发布端连接 RabbitMQ"),
            () -> assertTrue(hasBean(MessagingTopologyDeclarer.class), "声明发布方交换机"),
            () -> assertTrue(hasBean(DomainEventPublisher.class), "启用发布端"),
            () -> assertFalse(hasBean(DomainEventHandlerRegistry.class), "未启用消费端"),
            () -> assertFalse(hasBean(ConsumedEventRepository.class), "不加载消费记录 Mapper"),
            () -> assertFalse(hasBean(PartitionedListenerContainers.class), "没有监听容器"));
    }

    /** 上下文中是否存在该类型的 Bean。 */
    private boolean hasBean(Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }
}
