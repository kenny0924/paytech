package com.paytech.foundation.redis.core.processor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;

/**
 * 按资源锁模式串行处理请求。
 *
 * @author lzb
 */
public abstract class AbstractLockProcessor<Request, Response> extends AbstractProcessor<Request, Response> {
    private static final Logger log = LoggerFactory.getLogger(AbstractLockProcessor.class);

    @Autowired
    RedissonClient redissonClient;

    /** 获取资源锁，处理完成后释放。 */
    @Override
    protected Response processRequest(Request request) {
        LockCommand command = lockCommand(request);
        long startedAt = System.nanoTime();
        var acquired = new ArrayList<RLock>(command.keys.size());
        try {
            for (String key : command.keys) {
                long elapsed = Math.max(0, System.nanoTime() - startedAt);
                long remainingWait = Math.max(0, command.waitNanos - elapsed);
                acquired.add(acquireLock(command, key, remainingWait));
            }
            return processWithinLock(request);
        } finally {
            // 后续资源获取失败也释放已持有的资源，逆序与获取顺序配对。
            for (int index = acquired.size() - 1; index >= 0; index--) {
                releaseLock(acquired.get(index));
            }
        }
    }

    /**
     * 创建并获取一次锁，统一转换锁依赖异常；此边界不包含锁内业务执行。
     * 直接中断和 Redisson 包装的中断均恢复线程标记，其他 Redis 异常保留 Cause 并报告不可用。
     */
    private RLock acquireLock(LockCommand command, String key, long remainingWait) {
        try {
            RLock lock = command.spinLock ? redissonClient.getSpinLock(key) : redissonClient.getLock(key);
            switch (command.mode) {
                case LOCK -> lock.lockInterruptibly(command.leaseNanos, TimeUnit.NANOSECONDS);
                case TRY_LOCK -> {
                    if (!lock.tryLock(remainingWait, command.leaseNanos, TimeUnit.NANOSECONDS)) {
                        throw new ConcurrentRequestException("当前资源正在执行");
                    }
                }
            }
            return lock;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ConcurrentRequestException("获取资源锁被中断", failure);
        } catch (RedisException failure) {
            if (failure.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new ConcurrentRequestException("获取资源锁被中断", failure);
            }
            throw new ServiceUnavailableException(failure);
        }
    }

    /** 只释放当前线程持有的锁，释放失败不覆盖业务结果。 */
    private void releaseLock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            } else {
                log.warn("Processor 结束前资源锁已失效");
            }
        } catch (RuntimeException failure) {
            log.warn("Processor 释放资源锁失败，异常类型：{}", failure.getClass().getSimpleName());
        }
    }

    /**
     * 锁内处理
     * 
     * @param request
     *            请求
     */
    protected abstract Response processWithinLock(Request request);

    /**
     * 资源锁参数
     * 
     * @param request
     *            请求
     */
    protected abstract LockCommand lockCommand(Request request);

    /** 获取资源锁的方式。 */
    protected enum LockMode {
        /** 在等待时间内尝试获取。 */
        TRY_LOCK,

        /** 持续等待获取，允许线程中断。 */
        LOCK
    }

    /** 单资源兼容原毫秒工厂；多资源一次提交完整逻辑 Key 及共享时间预算。 */
    protected static class LockCommand {
        /** 完整逻辑 Key；Namespace 仍只由 Redisson NameMapper 添加一次。 */
        private final List<String> keys;

        /** 获取方式。 */
        private final LockMode mode;

        /** 本次全部资源共享的等待预算，单位为纳秒。 */
        private final long waitNanos;

        /** 每把锁的租约，单位为纳秒；-1 为单资源 Watchdog。 */
        private final long leaseNanos;

        /** 单资源保留 SpinLock；多资源兼容既有注解使用的普通 RLock。 */
        private final boolean spinLock;

        private LockCommand(List<String> keys, LockMode mode, long waitNanos, long leaseNanos, boolean spinLock) {
            this.keys = keys;
            this.mode = Objects.requireNonNull(mode, "mode");
            if (waitNanos < 0 || (leaseNanos != -1 && leaseNanos <= 0)) {
                throw new IllegalArgumentException("等待时间不能为负数，租约必须为正数或 -1");
            }
            this.waitNanos = waitNanos;
            this.leaseNanos = leaseNanos;
            this.spinLock = spinLock;
        }

        /** 保留单资源自动添加 lock: 前缀及毫秒参数合同。 */
        private LockCommand(String key, LockMode mode, int waitTime, int leaseTime) {
            this(List.of("lock:" + Objects.requireNonNull(key, "key")), mode,
                TimeUnit.MILLISECONDS.toNanos(waitTime),
                leaseTime == -1 ? -1 : TimeUnit.MILLISECONDS.toNanos(leaseTime), true);
        }

        /** 立即尝试获取，使用自动续租。 */
        public static LockCommand tryLock(String key) {
            return tryLock(key, -1);
        }

        /** 立即尝试获取，指定毫秒租约。 */
        public static LockCommand tryLock(String key, int leaseTime) {
            return tryLock(key, 0, leaseTime);
        }

        /** 在指定毫秒等待时间内尝试获取，指定毫秒租约。 */
        public static LockCommand tryLock(String key, int waitTime, int leaseTime) {
            return new LockCommand(key, LockMode.TRY_LOCK, waitTime, leaseTime);
        }

        /** 持续等待获取，使用自动续租。 */
        public static LockCommand lock(String key) {
            return lock(key, -1);
        }

        /** 持续等待获取，指定毫秒租约。 */
        public static LockCommand lock(String key, int leaseTime) {
            return new LockCommand(key, LockMode.LOCK, 0, leaseTime);
        }

        /**
         * 一次声明全部逻辑 Key，按 ASCII 顺序去重获取，共享一个等待预算。
         * Key 已包含资源前缀，不额外添加 lock: 或服务 Namespace；迁移方可保持既有互斥域。
         * 使用有限显式租约及普通 RLock，与既有注解消费者协调；业务自行提供原配置的 Duration。
         */
        public static LockCommand tryLocks(List<String> keys, Duration waitTime, Duration leaseTime) {
            Objects.requireNonNull(keys, "keys");
            if (keys.isEmpty() || keys.stream().anyMatch(key -> key == null
                || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,511}"))) {
                throw new IllegalArgumentException("资源锁 Key 集合不能为空且必须使用安全格式");
            }
            long waitNanos = Objects.requireNonNull(waitTime, "waitTime").toNanos();
            long leaseNanos = Objects.requireNonNull(leaseTime, "leaseTime").toNanos();
            if (leaseNanos < TimeUnit.MILLISECONDS.toNanos(1)) {
                throw new IllegalArgumentException("多资源锁必须使用不少于 1 毫秒的有限租约");
            }
            return new LockCommand(keys.stream().distinct().sorted().toList(), LockMode.TRY_LOCK,
                waitNanos, leaseNanos, false);
        }
    }
}
