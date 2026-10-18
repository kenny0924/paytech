package com.paytech.foundation.messaging.core.topology;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.listener.ListenerContainerConsumerFailedEvent;
import org.springframework.amqp.rabbit.listener.MissingQueueEvent;
import org.springframework.amqp.rabbit.support.ConsumerCancelledException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 按本服务已装配的参数幂等重声明发布与订阅拓扑（需求 2026-10-18，任务卡 01、02）。
 * <li>只重声明已登记的拓扑；自动配置在启动声明成功后才登记，因此首次声明路径不变。拓扑按单元管理：每个发布方一个单元，每个订阅一个单元。</li>
 * <li>新建连接：在创建连接的线程上同步重声明全部单元。Spring AMQP 先设置新连接再回调，声明复用该连接；回调返回后，监听容器的被动声明即可看到队列。</li>
 * <li>连接保持而拓扑被删：分区容器的消费者被 Broker 取消或被动声明找不到队列时，在容器线程上重声明该订阅；发布被拒且原因为发布方交换机
 * 不存在时，在发布线程上重声明发布方单元。同一单元的缺失触发同时只执行一次，正在等待重试的项不在缺失触发中重复尝试。</li>
 * <li>失败重试：单项失败（如同名队列参数冲突）记 WARN（含 Broker 原因），依赖它的绑定暂缓；失败项与暂缓项按 {@link TopologyRetryPolicy}
 * 有界指数退避重试，不限次数，直到成功或应用停止。连续失败达到阈值时记一次 ERROR，全部补齐后记 INFO 并重新计数。不向调用方抛出。</li>
 * <li>不加跨声明的锁：新建连接的回调持有连接工厂的锁，其他线程若在持有本类锁时再取连接会死锁。并发执行的声明本身幂等，待重试状态按每项完成顺序记录。</li>
 *
 * @author Claude
 */
public final class MessagingTopologyRecovery implements ConnectionListener, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MessagingTopologyRecovery.class);

    private final MessagingTopologyDeclarer declarer;

    private final TopologyRetryPolicy policy;

    private final Map<String, RecoveryUnit> units = new ConcurrentHashMap<>();

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("messaging-topology-retry").daemon(true).factory());

    private volatile boolean stopped;

    /**
     * 创建恢复组件，需由调用方注册到宿主 ConnectionFactory。
     *
     * @param declarer 拓扑声明器
     * @param policy 失败项重试策略
     */
    public MessagingTopologyRecovery(MessagingTopologyDeclarer declarer, TopologyRetryPolicy policy) {
        this.declarer = Objects.requireNonNull(declarer, "declarer must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 登记已在启动时声明成功的发布方交换机。
     *
     * @param producer 本服务名
     * @param subscribers 事件目录中订阅本服务事件的服务名，用于发布方交换机被删后补回订阅方绑定
     */
    public void registerProducer(String producer, Collection<String> subscribers) {
        Objects.requireNonNull(producer, "producer must not be null");
        List<String> copy = List.copyOf(subscribers);
        register(producerKey(producer), "producer " + producer,
            () -> declarer.producerDeclarations(producer, copy));
    }

    /**
     * 登记已在启动时声明成功的订阅拓扑；同一订阅重复登记以最后一次为准。
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @param partitions 分区数
     */
    public void registerSubscription(String consumer, String producer, int partitions) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        Objects.requireNonNull(producer, "producer must not be null");
        register(subscriptionKey(consumer, producer), "subscription " + consumer + " <- " + producer + " (" + partitions
            + " partitions)", () -> declarer.subscriptionDeclarations(consumer, producer, partitions));
    }

    /**
     * 新连接建立后重声明全部已登记拓扑，包括正在等待重试的项。
     */
    @Override
    public void onCreate(Connection connection) {
        if (stopped) {
            return;
        }
        units.values().forEach(unit -> run(unit, Mode.FULL));
    }

    /**
     * 订阅的监听容器事件入口：消费者被 Broker 取消或被动声明找不到队列时重声明该订阅；其他事件忽略。
     *
     * @param consumer 本服务名
     * @param producer 发布服务名
     * @return 设置到该订阅各分区监听容器上的事件发布器
     */
    public ApplicationEventPublisher subscriptionWatcher(String consumer, String producer) {
        String key = subscriptionKey(consumer, producer);
        return event -> {
            if (isQueueLoss(event)) {
                redeclareAfterLoss(key);
            }
        };
    }

    /**
     * 发布被 Broker 以发布方交换机不存在拒绝后重声明发布方单元；在发布线程上同步执行，返回后即可再次发布。
     *
     * @param producer 本服务名
     */
    public void producerExchangeMissing(String producer) {
        redeclareAfterLoss(producerKey(producer));
    }

    /**
     * 应用关闭时停止重试线程。
     */
    @Override
    public void destroy() {
        stopped = true;
        retryExecutor.shutdownNow();
    }

    private void register(String key, String name, Supplier<List<TopologyDeclaration>> plan) {
        units.put(key, new RecoveryUnit(name, plan));
    }

    private void redeclareAfterLoss(String key) {
        RecoveryUnit unit = units.get(key);
        if (stopped || unit == null || !unit.lossRedeclaring.compareAndSet(false, true)) {
            return;
        }
        try {
            run(unit, Mode.AFTER_LOSS);
        } finally {
            unit.lossRedeclaring.set(false);
        }
    }

    private void retry(RecoveryUnit unit) {
        unit.retryScheduled.set(false);
        if (stopped) {
            return;
        }
        try {
            run(unit, Mode.RETRY);
        } catch (RuntimeException unexpected) {
            log.error("拓扑重声明重试异常，稍后继续重试: {}", unit.name, unexpected);
            scheduleRetry(unit, unit.consecutiveFailures());
        }
    }

    private void run(RecoveryUnit unit, Mode mode) {
        Set<String> pendingBefore = unit.pendingSnapshot();
        Set<String> unresolved = new HashSet<>();
        int failures = 0;
        for (TopologyDeclaration declaration : unit.plan.get()) {
            boolean pending = pendingBefore.contains(declaration.item());
            if (!mode.attempts(pending)) {
                if (pending) {
                    unresolved.add(declaration.item());
                }
                continue;
            }
            if (declaration.dependsOn().stream().anyMatch(unresolved::contains)) {
                unresolved.add(declaration.item());
                unit.markPending(declaration.item());
                continue;
            }
            if (!attempt(unit, declaration)) {
                failures++;
                unresolved.add(declaration.item());
            }
        }
        conclude(unit, mode, failures);
    }

    private boolean attempt(RecoveryUnit unit, TopologyDeclaration declaration) {
        try {
            declaration.action().run();
            unit.markResolved(declaration.item());
            return true;
        } catch (AmqpException exception) {
            unit.markPending(declaration.item());
            log.warn("拓扑重声明失败，依赖该项的绑定暂缓，稍后重试: {} / {}", unit.name, declaration.item(), exception);
            return false;
        }
    }

    private void conclude(RecoveryUnit unit, Mode mode, int failures) {
        Conclusion conclusion = unit.conclude(failures > 0, policy.errorThreshold());
        if (conclusion.errorNow()) {
            log.error("拓扑重声明连续失败 {} 次，仍在按退避重试（不再重复记 ERROR）: {}，待声明 {}；请检查 Broker 上同名对象的参数或所需插件",
                conclusion.consecutiveFailures(), unit.name, conclusion.pending());
        }
        if (!conclusion.pending().isEmpty()) {
            scheduleRetry(unit, conclusion.consecutiveFailures());
            return;
        }
        if (conclusion.recovered()) {
            log.info("拓扑重声明已补齐: {}，此前连续失败 {} 次", unit.name, conclusion.recoveredAfter());
            return;
        }
        log.info("{}已重声明拓扑: {}", mode.description, unit.name);
    }

    private void scheduleRetry(RecoveryUnit unit, int consecutiveFailures) {
        if (stopped || !unit.retryScheduled.compareAndSet(false, true)) {
            return;
        }
        Duration delay = policy.delayAfter(consecutiveFailures);
        try {
            retryExecutor.schedule(() -> retry(unit), delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shutdown) {
            unit.retryScheduled.set(false);
        }
    }

    private static boolean isQueueLoss(Object event) {
        if (event instanceof MissingQueueEvent) {
            return true;
        }
        return event instanceof ListenerContainerConsumerFailedEvent failed
            && failed.getThrowable() instanceof ConsumerCancelledException;
    }

    private static String producerKey(String producer) {
        return "producer/" + producer;
    }

    private static String subscriptionKey(String consumer, String producer) {
        return "subscription/" + consumer + "/" + producer;
    }

    /** 执行方式：决定哪些项参与本次声明。 */
    private enum Mode {

        /** 新建连接：全部项，包括等待重试的项。 */
        FULL("连接恢复后"),

        /** 拓扑缺失触发：等待重试的项及其依赖项留给重试，其余项全部声明。 */
        AFTER_LOSS("检测到拓扑缺失后"),

        /** 退避重试：只声明等待重试的项。 */
        RETRY("重试后");

        private final String description;

        Mode(String description) {
            this.description = description;
        }

        private boolean attempts(boolean pending) {
            return switch (this) {
                case FULL -> true;
                case AFTER_LOSS -> !pending;
                case RETRY -> pending;
            };
        }
    }

    /**
     * 一次执行后的单元状态。
     *
     * @param pending 仍待声明的项
     * @param consecutiveFailures 连续失败次数（已补齐时为 0）
     * @param errorNow 本次是否首次达到 ERROR 阈值
     * @param recoveredAfter 本次补齐前的连续失败次数；未补齐或此前无失败为 0
     */
    private record Conclusion(List<String> pending, int consecutiveFailures, boolean errorNow, int recoveredAfter) {

        private boolean recovered() {
            return recoveredAfter > 0;
        }
    }

    /** 一个发布方或订阅的声明项与待重试状态；状态变更同步，声明本身不持锁。 */
    private static final class RecoveryUnit {

        private final String name;

        private final Supplier<List<TopologyDeclaration>> plan;

        private final AtomicBoolean lossRedeclaring = new AtomicBoolean();

        private final AtomicBoolean retryScheduled = new AtomicBoolean();

        private final Set<String> pending = new LinkedHashSet<>();

        private int consecutiveFailures;

        private boolean errorReported;

        private RecoveryUnit(String name, Supplier<List<TopologyDeclaration>> plan) {
            this.name = name;
            this.plan = plan;
        }

        private synchronized Set<String> pendingSnapshot() {
            return Set.copyOf(pending);
        }

        private synchronized void markPending(String item) {
            pending.add(item);
        }

        private synchronized void markResolved(String item) {
            pending.remove(item);
        }

        private synchronized int consecutiveFailures() {
            return consecutiveFailures;
        }

        private synchronized Conclusion conclude(boolean failed, int errorThreshold) {
            if (failed) {
                consecutiveFailures++;
            }
            boolean errorNow = failed && !errorReported && consecutiveFailures >= errorThreshold;
            errorReported = errorReported || errorNow;
            if (!pending.isEmpty()) {
                return new Conclusion(List.copyOf(pending), consecutiveFailures, errorNow, 0);
            }
            int recoveredAfter = consecutiveFailures;
            consecutiveFailures = 0;
            errorReported = false;
            return new Conclusion(List.of(), 0, errorNow, recoveredAfter);
        }
    }
}
