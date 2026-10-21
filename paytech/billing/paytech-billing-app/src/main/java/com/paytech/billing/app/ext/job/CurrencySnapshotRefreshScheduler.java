package com.paytech.billing.app.ext.job;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paytech.billing.app.configuration.CurrencySnapshotProperties;
import com.paytech.billing.core.common.CurrencySpecSnapshot;
import com.paytech.billing.integration.platform.PlatformCurrencyClient;

import lombok.AllArgsConstructor;

/**
 * 进程内调度入口：在事务外从 Platform 整体刷新币种快照（技术方案 2.3，Q3）
 * <li>启动后立即检查，之后按检查周期（默认 5 秒）检查：尚未加载、有刷新请求或距上次成功已满刷新间隔（默认 60 秒）时回源。</li>
 * <li>失败保留上一份快照、记 WARN 并累计连续失败次数；快照未加载时就绪探针保持 DOWN。</li>
 */
@Component
@AllArgsConstructor
public class CurrencySnapshotRefreshScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(CurrencySnapshotRefreshScheduler.class);

    private final Clock clock;
    private final CurrencySpecSnapshot snapshot;
    private final CurrencySnapshotProperties properties;
    private final PlatformCurrencyClient platformCurrencyClient;

    /** 定时检查，需要时刷新；未开启调度时不触发。 */
    @Scheduled(initialDelay = 0, fixedDelayString = "${billing.currency-snapshot.check-interval:PT5S}")
    public void refreshIfDue() {
        if (snapshot.refreshDue(clock.instant(), properties.getRefreshInterval())) {
            refresh();
        }
    }

    /**
     * 立即从 Platform 读取全部币种规则并整体替换快照
     * <li>同一实例内串行；调用方不得持有数据库事务。失败时保留旧快照，取走的刷新请求重新挂起，下一个检查周期重试。</li>
     *
     * @return 替换成功为 true；Platform 不可用或响应不合法为 false
     */
    public synchronized boolean refresh() {
        boolean requested = snapshot.takeRefreshRequest();
        try {
            snapshot.replace(platformCurrencyClient.listCurrencyRules(), clock.instant());
            return true;
        } catch (RuntimeException failure) {
            if (requested) {
                snapshot.requestRefresh();
            }
            int failures = snapshot.recordFailure();
            LOG.warn("Platform 币种快照刷新失败，保留上一份快照: loaded={}, consecutiveFailures={}, reason={}",
                snapshot.loaded(), failures, failure.toString());
            return false;
        }
    }
}
