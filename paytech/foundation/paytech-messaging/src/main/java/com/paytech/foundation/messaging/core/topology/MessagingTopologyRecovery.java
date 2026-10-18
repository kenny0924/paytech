package com.paytech.foundation.messaging.core.topology;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;

/**
 * Broker 连接每次新建（含断连后恢复）时，按本服务已装配的参数幂等重声明发布与订阅拓扑（需求 2026-10-18）。
 * <li>只重声明已登记的拓扑；自动配置在启动声明成功后才登记，因此首次声明路径不变。</li>
 * <li>在创建连接的线程上同步执行：Spring AMQP 先设置新连接再回调，声明复用该连接；回调返回后，监听容器的被动声明即可看到队列。</li>
 * <li>多个连接同时创建时串行执行，重复声明幂等无害。</li>
 * <li>单项失败（如同名队列参数冲突）由 {@link MessagingTopologyDeclarer} 记 ERROR，其余项继续，最后汇总记 ERROR；不向连接回调抛出，
 * 以免一项冲突使整个连接不可用。失败项不自动重试，修复 Broker 上的冲突后需重建连接或重启服务。</li>
 *
 * @author Claude
 */
public final class MessagingTopologyRecovery implements ConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(MessagingTopologyRecovery.class);

    private final MessagingTopologyDeclarer declarer;

    private final Set<String> producers = ConcurrentHashMap.newKeySet();

    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建恢复监听器，需由调用方注册到宿主 ConnectionFactory。
     *
     * @param declarer 拓扑声明器
     */
    public MessagingTopologyRecovery(MessagingTopologyDeclarer declarer) {
        this.declarer = Objects.requireNonNull(declarer, "declarer must not be null");
    }

    /**
     * 登记已在启动时声明成功的发布方交换机。
     *
     * @param producer 本服务名
     */
    public void registerProducer(String producer) {
        producers.add(Objects.requireNonNull(producer, "producer must not be null"));
    }

    /**
     * 登记已在启动时声明成功的订阅拓扑；同一订阅重复登记以最后一次为准。
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数
     */
    public void registerSubscription(String consumer, String producer, int partitions) {
        Subscription subscription = new Subscription(consumer, producer, partitions);
        subscriptions.put(consumer + "/" + producer, subscription);
    }

    /**
     * 新连接建立后重声明全部已登记拓扑。
     */
    @Override
    public void onCreate(Connection connection) {
        if (producers.isEmpty() && subscriptions.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            redeclareAll();
        } finally {
            lock.unlock();
        }
    }

    private void redeclareAll() {
        int failures = 0;
        for (String producer : producers) {
            failures += declarer.redeclareProducer(producer);
        }
        for (Subscription subscription : subscriptions.values()) {
            failures += declarer.redeclareSubscription(subscription.consumer(), subscription.producer(),
                subscription.partitions());
        }
        if (failures > 0) {
            log.error("连接恢复后拓扑重声明未完成: 失败 {} 项，producers={}, subscriptions={}；修复 Broker 上的冲突后需重建连接或重启服务",
                failures, producers, subscriptions.values());
            return;
        }
        log.info("连接恢复后已重声明拓扑: producers={}, subscriptions={}", producers, subscriptions.values());
    }

    private record Subscription(String consumer, String producer, int partitions) {

        private Subscription {
            Objects.requireNonNull(consumer, "consumer must not be null");
            Objects.requireNonNull(producer, "producer must not be null");
        }
    }
}
