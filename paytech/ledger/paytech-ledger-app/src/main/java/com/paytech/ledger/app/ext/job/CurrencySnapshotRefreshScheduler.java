package com.paytech.ledger.app.ext.job;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paytech.ledger.app.configuration.CurrencySnapshotProperties;
import com.paytech.ledger.integration.platform.CurrencySpecSnapshot;
import com.paytech.ledger.integration.platform.PlatformCurrencyClient;

import lombok.AllArgsConstructor;

/**
 * 进程内调度入口：在数据库事务外从 Platform 回源并整体替换币种规则快照（technique §2.3、Q3）
 * <li>启动后立即检查一次，此后每个检查周期（默认 5 秒）检查：尚未加载、有刷新请求或距上次成功加载满刷新间隔（默认 60 秒）时回源。</li>
 * <li>回源失败保留上一份快照、记 WARN 并累计连续失败次数；首次加载成功前就绪探针为 DOWN。</li>
 * <li>调度由 {@code ledger.currency-snapshot.scheduling-enabled} 控制；关闭时本组件仍装配，刷新只经公开方法触发。</li>
 */
@Component
@AllArgsConstructor
public class CurrencySnapshotRefreshScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(CurrencySnapshotRefreshScheduler.class);

    private final Clock clock;
    private final CurrencySpecSnapshot snapshot;
    private final PlatformCurrencyClient platformCurrencyClient;
    private final CurrencySnapshotProperties properties;

    /** 检查周期到达时按需回源。 */
    @Scheduled(initialDelay = 0, fixedDelayString = "${ledger.currency-snapshot.check-interval:PT5S}")
    public void refreshIfDue() {
        if (snapshot.isRefreshDue(clock.instant(), properties.getRefreshInterval())) {
            refresh();
        }
    }

    /**
     * 立即回源并整体替换快照；同一时刻只有一次回源
     * <li>先取走刷新请求再回源，回源期间新到的请求留给下一周期；失败时归还本次取走的请求。</li>
     * <li>Platform 不可用或响应不合法只影响本次刷新，不向调用方抛出。</li>
     */
    public synchronized void refresh() {
        boolean requested = snapshot.takeRefreshRequest();
        try {
            snapshot.replace(platformCurrencyClient.listCurrencyRules(), clock.instant());
        } catch (RuntimeException failure) {
            snapshot.recordFailure(requested);
            LOG.warn("Currency snapshot refresh failed, keeping previous snapshot: loaded={}, consecutiveFailures={}, "
                + "cause={}: {}", snapshot.isLoaded(), snapshot.getConsecutiveFailures(),
                failure.getClass().getSimpleName(), failure.getMessage());
        }
    }
}
