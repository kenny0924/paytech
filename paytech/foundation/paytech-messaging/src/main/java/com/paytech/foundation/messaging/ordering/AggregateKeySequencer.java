package com.paytech.foundation.messaging.ordering;

import java.util.Objects;

import com.paytech.foundation.compensation.api.CompensationExecutionSequencer;
import com.paytech.foundation.messaging.api.AggregateKey;

/**
 * 补偿事件执行编排的聚合串行实现（design/08 §3）：同一聚合的发布任务恒在同一条线程按 Claim 顺序执行，不同聚合并行。
 * <li>聚合由 {@code event_key} 决定：四段式键（{@link AggregateKey#eventKey}）取其 {@code aggregateType:aggregateId}；
 * 其他格式（例如现有 Payment / Compliance 以聚合 ID 本身作为事件键）以整个键为聚合。</li>
 * <li>线程队列已满时抛出 {@link java.util.concurrent.RejectedExecutionException}，Dispatcher 把任务按可重试失败释放，由扫描兜底。</li>
 * <li>消费端仍按 {@code aggregateVersion} 判定旧版本，发布侧串行只减少乱序，不替代消费侧判定。</li>
 *
 * @author Claude
 */
public final class AggregateKeySequencer implements CompensationExecutionSequencer, AutoCloseable {

    private final AggregateSerialExecutor executor;

    /**
     * 创建编排器。
     *
     * @param executor 聚合串行执行器，由本编排器负责关闭
     */
    public AggregateKeySequencer(AggregateSerialExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * 在事件键所属聚合的固定线程上排队执行。
     */
    @Override
    public void execute(String eventType, String eventKey, Runnable task) {
        executor.execute(aggregateOf(eventKey), task);
    }

    /**
     * 事件键对应的串行聚合标识。
     *
     * @param eventKey 补偿事件业务键
     * @return 四段式键取 {@code aggregateType:aggregateId}，其他格式为键本身
     */
    public static String aggregateOf(String eventKey) {
        Objects.requireNonNull(eventKey, "eventKey must not be null");
        try {
            return AggregateKey.fromEventKey(eventKey).toString();
        } catch (IllegalArgumentException notFourPartKey) {
            return eventKey;
        }
    }

    /**
     * 事件键落到的线程序号。
     *
     * @param eventKey 补偿事件业务键
     * @return 线程序号
     */
    public int laneOf(String eventKey) {
        return executor.laneOf(aggregateOf(eventKey));
    }

    /**
     * 正在执行与排队等待的任务数。
     *
     * @return 任务数
     */
    public int pendingTaskCount() {
        return executor.pendingTaskCount();
    }

    /**
     * 立即停止全部线程；未执行任务仍持有 Lease，到期后由扫描接管。
     */
    @Override
    public void close() {
        executor.close();
    }
}
