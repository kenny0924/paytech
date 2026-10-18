package com.paytech.foundation.compensation.api;

/**
 * 已 Claim 补偿任务的执行编排端口：决定任务在哪条线程、以什么顺序执行（design/08 §3）。
 * <li>默认 {@link #DIRECT}：在协调线程上直接执行，即原有的并发语义。</li>
 * <li>宿主可提供一个 Spring Bean 替换默认实现，例如按 {@code event_key} 的聚合前缀把同一聚合的任务串行到固定线程；
 * 本组件不感知消息中间件，实现由宿主或其他 Foundation 模块提供。</li>
 * <li>扫描、Claim/Lease 与分片均不受影响；排队等待期间任务仍持有 Lease，实现应保持有界队列。</li>
 *
 * @author Claude
 */
@FunctionalInterface
public interface CompensationExecutionSequencer {

    /** 在当前协调线程直接执行，保持原有并发。 */
    CompensationExecutionSequencer DIRECT = (eventType, eventKey, task) -> task.run();

    /**
     * 安排执行一个已 Claim 任务；任务自身负责结束状态迁移与异常隔离。
     *
     * @param eventType 任务事件类型
     * @param eventKey 任务业务键
     * @param task 执行任务
     * @throws java.util.concurrent.RejectedExecutionException 暂时无法接收；协调器按可重试失败处理
     */
    void execute(String eventType, String eventKey, Runnable task);
}
