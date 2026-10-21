package com.paytech.ledger.integration.platform;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.paytech.ledger.domain.CurrencyRule;

/**
 * Platform 币种规则的进程内全量快照（technique §2.3）
 * <li>读方法只读内存、从不外呼，可在过账事务内使用；快照由刷新入口在事务外整体原子替换，刷新失败保留上一份。</li>
 * <li>首次加载成功前为“未加载”，此时任何币种都查不到，就绪探针为 DOWN。</li>
 * <li>“待刷新”标记由查不到币种的业务读取置位，刷新开始时取走、失败时归还，使下一个检查周期继续回源。</li>
 */
@Component
public class CurrencySpecSnapshot {
    private final AtomicReference<Loaded> loaded = new AtomicReference<>();
    private final AtomicBoolean refreshRequested = new AtomicBoolean();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** 按币种代码读取规则；未加载或不存在时返回空。 */
    public CurrencyRule find(String currency) {
        Loaded current = loaded.get();
        return current == null ? null : current.rules().get(currency);
    }

    /** 请求尽快回源刷新（只置标记，不外呼）。 */
    public void requestRefresh() {
        refreshRequested.set(true);
    }

    /** 是否有待处理的刷新请求。 */
    public boolean isRefreshRequested() {
        return refreshRequested.get();
    }

    /** 取走刷新请求标记，返回取走前是否存在请求。 */
    public boolean takeRefreshRequest() {
        return refreshRequested.getAndSet(false);
    }

    /** 是否至少成功加载过一次。 */
    public boolean isLoaded() {
        return loaded.get() != null;
    }

    /** 最近一次成功加载的时间；未加载时为空。 */
    public Instant getLoadedAt() {
        Loaded current = loaded.get();
        return current == null ? null : current.loadedAt();
    }

    /** 自上次成功加载以来连续失败的刷新次数。 */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 判断本次检查是否需要回源
     *
     * @param now 当前时间
     * @param refreshInterval 就绪后的全量刷新间隔
     * @return 未加载、有刷新请求或距上次成功加载已满间隔时为 true
     */
    public boolean isRefreshDue(Instant now, Duration refreshInterval) {
        Loaded current = loaded.get();
        return current == null || refreshRequested.get() || !now.isBefore(current.loadedAt().plus(refreshInterval));
    }

    /** 以一份完整规则整体替换快照，并清零连续失败次数。 */
    public void replace(Map<String, CurrencyRule> rules, Instant loadedAt) {
        loaded.set(new Loaded(Map.copyOf(rules), loadedAt));
        consecutiveFailures.set(0);
    }

    /**
     * 记录一次失败的刷新，保留上一份快照
     *
     * @param requested 本次刷新是否由刷新请求触发；是则归还请求，下一个检查周期继续回源
     */
    public void recordFailure(boolean requested) {
        consecutiveFailures.incrementAndGet();
        if (requested) {
            refreshRequested.set(true);
        }
    }

    /** 一份已加载的不可变快照及其加载时间。 */
    private record Loaded(Map<String, CurrencyRule> rules, Instant loadedAt) {
    }
}
