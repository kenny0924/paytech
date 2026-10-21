package com.paytech.billing.core.common;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.billing.domain.CurrencyRule;

/**
 * Platform 币种规则的进程内全量快照（技术方案 2.3，Q6）
 * <li>读方法只读内存，从不外呼，可在数据库事务内使用；快照由刷新调度在事务外整体原子替换，不逐条过期。</li>
 * <li>首次加载成功前为空；刷新失败保留上一份快照并累计连续失败次数，下一次成功清零。</li>
 * <li>业务查不到币种时置“待刷新”，由刷新调度在下一个检查周期回源。</li>
 */
@Component
public class CurrencySpecSnapshot {
    /** 已加载的规则与加载时间；首次加载前为空。 */
    private final AtomicReference<Loaded> loaded = new AtomicReference<>();

    /** 自上次成功起连续刷新失败的次数。 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** 业务查不到币种后请求的刷新，尚未被刷新取走。 */
    private final AtomicBoolean refreshRequested = new AtomicBoolean();

    /** 按币种代码读取规则；未加载或不存在返回空。 */
    public CurrencyRule find(String currency) {
        Loaded current = loaded.get();
        return current == null ? null : current.rules().get(currency);
    }

    /** 是否已完成首次加载。 */
    public boolean loaded() {
        return loaded.get() != null;
    }

    /** 最近一次成功加载的时间；未加载返回空。 */
    public Instant loadedAt() {
        Loaded current = loaded.get();
        return current == null ? null : current.loadedAt();
    }

    /** 自上次成功起连续刷新失败的次数。 */
    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    /** 是否有待处理的刷新请求。 */
    public boolean refreshRequested() {
        return refreshRequested.get();
    }

    /** 业务查不到币种时请求在下一个检查周期回源。 */
    public void requestRefresh() {
        refreshRequested.set(true);
    }

    /** 取走待处理的刷新请求；刷新失败时由调用方重新请求。 */
    public boolean takeRefreshRequest() {
        return refreshRequested.getAndSet(false);
    }

    /**
     * 是否需要刷新
     *
     * @param now 当前时间
     * @param refreshInterval 就绪后的全量刷新间隔
     * @return 尚未加载、有刷新请求或距上次成功已满刷新间隔时为 true
     */
    public boolean refreshDue(Instant now, Duration refreshInterval) {
        Loaded current = loaded.get();
        return current == null || refreshRequested.get() || !now.isBefore(current.loadedAt().plus(refreshInterval));
    }

    /**
     * 以一份已校验的完整规则整体替换快照并清零连续失败次数
     *
     * @param rules Platform 全部币种规则，币种代码不重复
     * @param now 加载时间
     */
    public void replace(List<CurrencyRule> rules, Instant now) {
        Map<String, CurrencyRule> byCurrency = rules.stream()
            .collect(Collectors.toUnmodifiableMap(CurrencyRule::getCurrency, Function.identity()));
        loaded.set(new Loaded(byCurrency, now));
        consecutiveFailures.set(0);
    }

    /** 记录一次刷新失败，返回连续失败次数；已有快照保持不变。 */
    public int recordFailure() {
        return consecutiveFailures.incrementAndGet();
    }

    /** 不可变的已加载快照。 */
    private record Loaded(Map<String, CurrencyRule> rules, Instant loadedAt) {
    }
}
