package com.paytech.ledger.app.configuration;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/**
 * Platform 币种规则快照的刷新配置（technique §2.3、Q3）
 * <li>检查周期 {@code ledger.currency-snapshot.check-interval} 与调度开关 {@code ledger.currency-snapshot.scheduling-enabled}
 * 由调度注解与条件装配直接读取，不在本类重复。</li>
 */
@Getter
@ConfigurationProperties("ledger.currency-snapshot")
public class CurrencySnapshotProperties {
    /**
     * 就绪后的全量刷新间隔
     * <li>必填，生产为 PT60S；距上次成功加载满此间隔时下一个检查周期回源，Platform 新启用的币种最迟约在此间隔后被识别。</li>
     */
    private final Duration refreshInterval;

    /** 缺少刷新间隔时启动失败。 */
    public CurrencySnapshotProperties(Duration refreshInterval) {
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "ledger.currency-snapshot.refresh-interval");
    }
}
