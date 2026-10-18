package com.paytech.foundation.messaging.test.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * 本次运行在共享 Broker 上的测试拓扑：观察队列、读取 DLQ、直接投递原始消息；每例清空队列，上下文关闭时删除全部拓扑。
 */
@TestComponent
public final class BrokerTestSupport implements Clear, DisposableBean {

    private final RabbitAdmin admin;
    private final RabbitTemplate template;

    /**
     * 创建支持组件。
     *
     * @param connectionFactory 测试连接
     */
    public BrokerTestSupport(ConnectionFactory connectionFactory) {
        this.admin = new RabbitAdmin(connectionFactory);
        this.template = new RabbitTemplate(connectionFactory);
    }

    /**
     * 测试订阅的全部分区队列名。
     *
     * @return 队列名
     */
    public List<String> partitionQueues() {
        List<String> queues = new ArrayList<>();
        for (int partition = 0; partition < TestRun.PARTITIONS; partition++) {
            queues.add(MessagingTopology.partitionQueue(TestRun.CONSUMER, TestRun.PRODUCER, partition));
        }
        return queues;
    }

    /**
     * 死信队列名。
     *
     * @return 队列名
     */
    public String deadLetterQueue() {
        return MessagingTopology.deadLetterQueue(TestRun.CONSUMER, TestRun.PRODUCER);
    }

    /**
     * 队列中待投递的消息数。
     *
     * @param queue 队列名
     * @return 消息数；队列不存在为 -1
     */
    public int readyMessages(String queue) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? -1 : ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }

    /**
     * 从死信队列取一条消息（不等待）。
     *
     * @return 消息或 null
     */
    public Message receiveDeadLetter() {
        return template.receive(deadLetterQueue());
    }

    /**
     * 绕过发布端，直接向一个分区队列投递原始字节。
     *
     * @param queue 分区队列
     * @param body 消息体
     */
    public void sendRaw(String queue, byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        template.send("", queue, new Message(body, properties));
    }

    /**
     * 从发布方交换机的一个临时绑定队列读取原始消息，用于检查 AMQP 属性。
     *
     * @param queue 临时队列名
     * @return 消息或 null
     */
    public Message receive(String queue) {
        return template.receive(queue, 5_000);
    }

    /**
     * 声明一个绑定到发布方交换机的临时观察队列。
     *
     * @param queue 队列名
     * @param producer 发布服务名
     */
    public void declareObserver(String queue, String producer) {
        admin.declareQueue(new org.springframework.amqp.core.Queue(queue, false, false, true));
        admin.declareBinding(new org.springframework.amqp.core.Binding(queue,
            org.springframework.amqp.core.Binding.DestinationType.QUEUE, MessagingTopology.producerExchange(producer),
            "", java.util.Map.of()));
    }

    /**
     * 删除临时观察队列。
     *
     * @param queue 队列名
     */
    public void deleteQueue(String queue) {
        admin.deleteQueue(queue);
    }

    /**
     * 删除一个交换机（模拟运维误删或策略清理），连接保持不变。
     *
     * @param exchange 交换机名
     */
    public void deleteExchange(String exchange) {
        admin.deleteExchange(exchange);
    }

    /** 每例清空分区队列与 DLQ。 */
    @Override
    public void clear() {
        partitionQueues().forEach(queue -> admin.purgeQueue(queue, false));
        admin.purgeQueue(deadLetterQueue(), false);
    }

    /**
     * 模拟 Broker 丢失订阅侧拓扑：删除本次运行的分区队列、DLQ、DLX 与一致性哈希交换机，保留发布方交换机。
     */
    public void deleteSubscriptionTopology() {
        partitionQueues().forEach(admin::deleteQueue);
        admin.deleteQueue(deadLetterQueue());
        admin.deleteExchange(MessagingTopology.deadLetterExchange(TestRun.CONSUMER, TestRun.PRODUCER));
        admin.deleteExchange(MessagingTopology.consumerHashExchange(TestRun.CONSUMER, TestRun.PRODUCER));
    }

    /**
     * 模拟 Broker 重建：删除本次运行发布与订阅的全部拓扑。
     */
    public void deleteRunTopology() {
        deleteSubscriptionTopology();
        admin.deleteExchange(MessagingTopology.producerExchange(TestRun.PRODUCER));
    }

    /**
     * 声明一个无参数的持久经典队列，用于制造与生产拓扑同名但参数不同的冲突。
     *
     * @param queue 队列名
     */
    public void declareClassicQueue(String queue) {
        admin.declareQueue(new org.springframework.amqp.core.Queue(queue, true, false, false));
    }

    /** 上下文关闭时删除本次运行声明的全部队列与交换机。 */
    @Override
    public void destroy() {
        deleteRunTopology();
        admin.deleteExchange(MessagingTopology.producerExchange(TestRun.ORPHAN_PRODUCER));
        admin.deleteExchange(MessagingTopology.producerExchange(TestRun.MISSING_PRODUCER));
    }
}
