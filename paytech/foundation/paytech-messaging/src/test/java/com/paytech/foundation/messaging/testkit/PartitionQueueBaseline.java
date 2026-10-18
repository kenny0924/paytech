package com.paytech.foundation.messaging.testkit;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.context.SmartLifecycle;

import com.paytech.foundation.messaging.consume.PartitionedListenerContainers;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.foundation.messaging.core.topology.MessagingTopologyDeclarer;

/**
 * 消费方 {@code auto} 测试的分区队列基线：在本服务分区监听容器启动之前，清空本服务订阅的全部分区队列与 DLQ。
 * <li>分区队列是持久 Quorum 队列，其他模块的测试（例如 payment 真实发布 PaymentSucceeded）会在其中留下消息；监听容器一启动就会消费，
 * 测试基类的 {@code @BeforeEach} 清理已经太晚。</li>
 * <li>{@link #PHASE} 低于 {@link PartitionedListenerContainers}（默认 phase {@link SmartLifecycle#DEFAULT_PHASE}）与
 * {@code RabbitListenerEndpointRegistry}（{@link Integer#MAX_VALUE}），因此在任何消费开始前执行。</li>
 * <li>清空前用生产声明器按相同参数重复声明拓扑（幂等），队列尚不存在时由此创建；声明或清空的任何异常都向上抛出，使上下文启动失败。</li>
 * <li>只清理传入的本服务队列，不触碰其他服务的队列；只用于测试类路径。</li>
 *
 * @author Claude
 */
public final class PartitionQueueBaseline implements SmartLifecycle {

    /** 启动阶段：紧邻分区监听容器之前。 */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 1;

    private static final Logger log = LoggerFactory.getLogger(PartitionQueueBaseline.class);

    private final AmqpAdmin admin;

    private final MessagingTopologyDeclarer declarer;

    private final String consumer;

    private final Map<String, Integer> partitionsByProducer;

    private volatile boolean running;

    /**
     * 创建基线组件。
     *
     * @param admin 清空队列使用的 AmqpAdmin
     * @param declarer 生产拓扑声明器
     * @param consumer 本服务名
     * @param partitionsByProducer 订阅的发布方及其分区数
     */
    public PartitionQueueBaseline(AmqpAdmin admin, MessagingTopologyDeclarer declarer, String consumer,
        Map<String, Integer> partitionsByProducer) {
        this.admin = Objects.requireNonNull(admin, "admin must not be null");
        this.declarer = Objects.requireNonNull(declarer, "declarer must not be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.partitionsByProducer = Map.copyOf(partitionsByProducer);
    }

    /**
     * 声明并清空每个订阅的分区队列与 DLQ。
     */
    @Override
    public void start() {
        partitionsByProducer.forEach(this::resetSubscription);
        running = true;
    }

    /**
     * 无需释放资源。
     */
    @Override
    public void stop() {
        running = false;
    }

    /**
     * 是否已执行清空。
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 启动阶段，见 {@link #PHASE}。
     */
    @Override
    public int getPhase() {
        return PHASE;
    }

    /**
     * 订阅的发布方及其分区数。
     *
     * @return 不可变映射
     */
    public Map<String, Integer> subscriptions() {
        return partitionsByProducer;
    }

    private void resetSubscription(String producer, int partitions) {
        declarer.declareSubscription(consumer, producer, partitions);
        int purged = 0;
        for (int partition = 0; partition < partitions; partition++) {
            purged += admin.purgeQueue(MessagingTopology.partitionQueue(consumer, producer, partition));
        }
        purged += admin.purgeQueue(MessagingTopology.deadLetterQueue(consumer, producer));
        log.info("测试分区队列基线: consumer={}, producer={}, partitions={}, 清除残留消息={}", consumer, producer,
            partitions, purged);
    }
}
