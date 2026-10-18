package com.paytech.client.console.bff.service.processor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.MDC;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

/**
 * 页面聚合的并发只读工具：子任务沿用请求日志坐标，在共享截止时间内等待结果
 * <li>超时或依赖失败只让对应区域显示不可用；会话失效仍终止整页。</li>
 */
final class ConcurrentPageReads {
    private ConcurrentPageReads() { }

    /** 子任务继承当前请求的 MDC，结束后清除虚拟线程上下文。 */
    static <T> Callable<T> withRequestContext(Supplier<T> action) {
        Map<String, String> values = MDC.getCopyOfContextMap();
        return () -> {
            if (values != null) {
                MDC.setContextMap(values);
            }
            try {
                return action.get();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * 在截止时间内取得子任务结果。
     *
     * @param future 子任务；为 null 表示该区域不适用
     * @param deadline {@link System#nanoTime()} 基准的截止时刻
     * @return 结果；任务为 null、超时或失败时返回 null，会话失效直接抛出
     */
    static <T> T result(Future<T> future, long deadline) {
        if (future == null) {
            return null;
        }
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof FailException rejected
                && ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID.getCode().equals(rejected.getCode())) {
                throw rejected;
            }
            return null;
        } catch (TimeoutException unavailable) {
            future.cancel(true);
            return null;
        }
    }
}
