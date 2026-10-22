package com.paytech.platform.support.dataquery.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据源连接池的多实例刷新
 * <li>前缀 data-query.datasource；修改需重启。修改配置的实例在提交后立即刷新本地，其他实例最多等一个刷新周期。</li>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "data-query.datasource")
public class DataSourceRefreshProperties {

    /**
     * 是否启用定时刷新
     * <li>默认 true；自动测试关闭，由用例显式触发刷新，避免后台任务在清理期间重建连接池。</li>
     */
    private boolean refreshEnabled = true;

    /**
     * 定时刷新间隔
     * <li>默认 5s；每轮只读 t_data_source 的 ID、版本与状态，版本变化才读取 Vault 并重建连接池。</li>
     */
    private Duration refreshInterval = Duration.ofSeconds(5);

    /** 启动时拒绝空值与非正间隔。 */
    @AssertTrue(message = "data-query.datasource.refresh-interval 必须为正时长")
    public boolean isRefreshIntervalValid() {
        return refreshInterval != null && !refreshInterval.isNegative() && !refreshInterval.isZero();
    }
}
