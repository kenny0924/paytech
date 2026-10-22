package com.paytech.platform.support.ext.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;

import lombok.AllArgsConstructor;

/**
 * 数据源连接池的多实例刷新入口（technique 5.3）：每个周期比较数据源版本与状态，按需重建或退役连接池。
 * <li>间隔 data-query.datasource.refresh-interval（默认 PT5S）；data-query.datasource.refresh-enabled=false 时不装配。</li>
 * <li>不使用 MQ 广播；单个数据源失败由注册表保留旧池，整轮失败（如本库不可用）只记日志，下一轮重试。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "data-query.datasource.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class DataSourcePoolRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolRefreshScheduler.class);

    private final DataSourcePoolRegistry pools;

    /** 定时刷新；异常不向调度器抛出，避免任务被取消。 */
    @Scheduled(initialDelayString = "${data-query.datasource.refresh-interval:PT5S}",
        fixedDelayString = "${data-query.datasource.refresh-interval:PT5S}")
    public void refresh() {
        try {
            pools.refresh();
        } catch (RuntimeException failure) {
            log.warn("数据源连接池刷新失败，下一轮重试，异常类型={}", failure.getClass().getSimpleName());
        }
    }
}
