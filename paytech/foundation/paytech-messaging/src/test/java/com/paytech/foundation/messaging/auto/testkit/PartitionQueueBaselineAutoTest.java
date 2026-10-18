package com.paytech.foundation.messaging.auto.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.messaging.consume.PartitionedListenerContainers;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;
import com.paytech.foundation.messaging.test.base.MessagingMockTestBase;
import com.paytech.foundation.messaging.test.support.TestRun;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaseline;

/**
 * 消费方测试的分区队列基线：在监听容器之前启动、清空残留消息、首次运行时声明拓扑、声明冲突时启动失败。
 * <li>残留场景使用本次运行前缀下独立的订阅服务名，不影响共享上下文中正在消费的测试拓扑。</li>
 *
 * @see PartitionQueueBaseline#start()
 */
class PartitionQueueBaselineAutoTest extends MessagingMockTestBase {

    private static final String BASELINE_CONSUMER = TestRun.PREFIX + "-baseline";

    private static final int BASELINE_PARTITIONS = 2;

    @Autowired
    private PartitionQueueBaseline hostBaseline;

    @Autowired
    private PartitionedListenerContainers containers;

    @Autowired
    private MessagingTopologyDeclarer declarer;

    @Autowired
    private ConnectionFactory connectionFactory;

    @AfterEach
    void deleteBaselineTopology() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        for (int partition = 0; partition < BASELINE_PARTITIONS; partition++) {
            admin.deleteQueue(MessagingTopology.partitionQueue(BASELINE_CONSUMER, TestRun.PRODUCER, partition));
        }
        admin.deleteQueue(MessagingTopology.deadLetterQueue(BASELINE_CONSUMER, TestRun.PRODUCER));
        admin.deleteExchange(MessagingTopology.deadLetterExchange(BASELINE_CONSUMER, TestRun.PRODUCER));
        admin.deleteExchange(MessagingTopology.consumerHashExchange(BASELINE_CONSUMER, TestRun.PRODUCER));
    }

    @Test
    void test_normalFlow_hostBaselineStartsBeforeListenerContainers() {
        assertTrue(hostBaseline.isRunning());
        assertTrue(hostBaseline.getPhase() < containers.getPhase(),
            () -> "baseline phase " + hostBaseline.getPhase() + " >= containers phase " + containers.getPhase());
        assertTrue(hostBaseline.getPhase() < Integer.MAX_VALUE);
        assertEquals(Map.of(TestRun.PRODUCER, TestRun.PARTITIONS), hostBaseline.subscriptions());
    }

    @Test
    void test_normalFlow_residualMessagesPurged() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        declarer.declareSubscription(BASELINE_CONSUMER, TestRun.PRODUCER, BASELINE_PARTITIONS);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        String firstPartition = MessagingTopology.partitionQueue(BASELINE_CONSUMER, TestRun.PRODUCER, 0);
        String lastPartition = MessagingTopology.partitionQueue(BASELINE_CONSUMER, TestRun.PRODUCER, 1);
        String deadLetter = MessagingTopology.deadLetterQueue(BASELINE_CONSUMER, TestRun.PRODUCER);
        send(template, firstPartition);
        send(template, firstPartition);
        send(template, lastPartition);
        send(template, deadLetter);
        assertEquals(2, readyMessages(admin, firstPartition));

        baseline(admin).start();

        assertEquals(0, readyMessages(admin, firstPartition));
        assertEquals(0, readyMessages(admin, lastPartition));
        assertEquals(0, readyMessages(admin, deadLetter));
    }

    @Test
    void test_edgeCase_missingTopologyDeclaredBeforePurge() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        String firstPartition = MessagingTopology.partitionQueue(BASELINE_CONSUMER, TestRun.PRODUCER, 0);
        assertEquals(-1, readyMessages(admin, firstPartition));

        PartitionQueueBaseline baseline = baseline(admin);
        baseline.start();

        assertTrue(baseline.isRunning());
        assertEquals(0, readyMessages(admin, firstPartition));
        assertEquals(0, readyMessages(admin, MessagingTopology.deadLetterQueue(BASELINE_CONSUMER, TestRun.PRODUCER)));
    }

    @Test
    void test_businessRuleViolation_conflictingQueueFailsStart() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // 同名经典队列与生产 Quorum 参数冲突：Broker 拒绝重复声明，基线不得吞掉该异常。
        admin.declareQueue(new Queue(MessagingTopology.partitionQueue(BASELINE_CONSUMER, TestRun.PRODUCER, 0), true));
        PartitionQueueBaseline baseline = baseline(admin);

        assertThrows(AmqpException.class, baseline::start);
        assertFalse(baseline.isRunning());
    }

    private PartitionQueueBaseline baseline(RabbitAdmin admin) {
        return new PartitionQueueBaseline(admin, declarer, BASELINE_CONSUMER,
            Map.of(TestRun.PRODUCER, BASELINE_PARTITIONS));
    }

    private static void send(RabbitTemplate template, String queue) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        template.send("", queue, new Message("{\"residue\":true}".getBytes(StandardCharsets.UTF_8), properties));
    }

    private static int readyMessages(RabbitAdmin admin, String queue) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? -1 : ((Number)properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }
}
