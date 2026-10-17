package com.paytech.platform.support.integration.domain.execution;

/** 流程总预算：初始化与全部节点共享，按单调时钟计算剩余时间。 */
public final class Deadline {

    private final long expiresAtNanos;

    private Deadline(long expiresAtNanos) {
        this.expiresAtNanos = expiresAtNanos;
    }

    /** 从现在开始计算预算。 */
    public static Deadline start(long timeoutMs) {
        return new Deadline(System.nanoTime() + timeoutMs * 1_000_000L);
    }

    /** 剩余毫秒，耗尽时为 0。 */
    public long remainingMillis() {
        return Math.max(0, (expiresAtNanos - System.nanoTime()) / 1_000_000L);
    }

    /** 预算是否已耗尽。 */
    public boolean expired() {
        return remainingMillis() <= 0;
    }
}
