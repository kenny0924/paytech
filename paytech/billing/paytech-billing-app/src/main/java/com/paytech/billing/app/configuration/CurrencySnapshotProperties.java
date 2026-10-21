package com.paytech.billing.app.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Platform 币种快照的刷新节奏（技术方案 2.3，Q3）
 * <li>检查周期 {@code billing.currency-snapshot.check-interval}（默认 PT5S）与调度开关
 * {@code billing.currency-snapshot.scheduling-enabled}（默认 true）由调度注解与条件装配直接读取，不在此重复。</li>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("billing.currency-snapshot")
public class CurrencySnapshotProperties {
    /**
     * 就绪后的全量刷新间隔
     * <li>默认 PT60S：Platform 新启用的币种最迟一个间隔后被识别；启动时立即加载，未加载或有刷新请求时按检查周期回源。</li>
     */
    @NotNull
    private Duration refreshInterval = Duration.ofSeconds(60);
}
