package com.paytech.platform.support.dataquery.engine;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.dataquery.core.configuration.DataQueryJointProperties;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;

/**
 * 联合查询的有界执行器（Bean 名 dataQueryJointExecutor）：固定线程数、有界队列，满时拒绝。
 * <li>自持线程池而不注册为 Spring Executor，避免替换 Spring Boot 的默认任务执行器。</li>
 * <li>提交时复制调用线程的 MDC（请求标识），任务线程不持有数据库事务。</li>
 * <li>启动时校验 data-query.joint.step-budget 不大于 data-query.limits.max-timeout。</li>
 */
@Component("dataQueryJointExecutor")
public class JointQueryExecutor implements DisposableBean {

    private final ThreadPoolExecutor pool;

    /**
     * 按配置创建线程池。
     *
     * @param properties 联合查询配置
     * @param limits 数据查询硬上限
     */
    public JointQueryExecutor(DataQueryJointProperties properties, DataQueryLimitsProperties limits) {
        if (properties.getStepBudget().compareTo(limits.getMaxTimeout()) > 0) {
            throw new IllegalStateException("data-query.joint.step-budget 不能大于 data-query.limits.max-timeout");
        }
        AtomicInteger sequence = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(properties.getExecutorThreads(), properties.getExecutorThreads(), 60,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(properties.getExecutorQueue()), runnable -> {
                Thread thread = new Thread(runnable, "data-query-joint-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        this.pool.allowCoreThreadTimeOut(true);
    }

    /**
     * 提交一个任务。
     *
     * @param task 任务
     * @return Future
     * @throws RejectedExecutionException 队列已满
     */
    public <T> Future<T> submit(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return pool.submit(() -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                return task.call();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        });
    }

    /** 关闭时中断仍在执行的任务。 */
    @Override
    public void destroy() {
        pool.shutdownNow();
    }
}
