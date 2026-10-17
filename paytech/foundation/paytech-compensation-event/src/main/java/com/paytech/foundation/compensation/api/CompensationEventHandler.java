package com.paytech.foundation.compensation.api;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

/**
 * 执行一种范围明确且具备幂等性的补偿任务。
 *
 * <p>
 * 处理器通常使用 {@link CompensationEvent#eventKey()} 和 {@link CompensationEvent#subEventType()}
 * 从本服务数据库加载一条准确的持久化事实或操作。RabbitMQ 处理器会序列化不可变事实记录，因此重试不会发布不同的事实。 基础库不维护第二种执行类型。
 *
 * @author Codex
 */
public interface CompensationEventHandler {

    /**
     * 返回当前 Bean 处理的唯一事件类型。
     *
     * @return 事件类型
     */
    String eventType();

    /**
     * 返回该处理器的超时与重试策略。
     *
     * @return 执行策略
     */
    default CompensationExecutionPolicy policy() {
        return CompensationExecutionPolicy.defaults();
    }

    /**
     * 执行一次幂等尝试。未分类的运行时异常默认按结果未知处理，并受最大次数约束。
     *
     * @param event
     *            最小事件引用
     * @return 明确的执行结果
     * @throws FailException
     *             结果已经明确失败，Dispatcher 不再自动重试
     * @throws ErrorException
     *             结果未知，Dispatcher 按执行策略进行有界重试
     */
    CompensationHandlerResult handle(CompensationEvent event);
}
