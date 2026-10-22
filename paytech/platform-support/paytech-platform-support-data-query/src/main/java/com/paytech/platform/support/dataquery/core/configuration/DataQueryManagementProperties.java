package com.paytech.platform.support.dataquery.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据源与模板维护写接口的资源锁参数
 * <li>前缀 data-query.management；锁 Key 为 lock:data-query:source:{id} 与 lock:data-query:template:{id}，
 * 版本条件更新与唯一索引仍为最终保护。修改需重启。</li>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "data-query.management")
public class DataQueryManagementProperties {

    /**
     * TRY_LOCK 最长等待时间
     * <li>默认 500ms；超时返回 409。</li>
     */
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 资源锁显式租约
     * <li>默认 60s，须覆盖锁内的 Vault 调用、连接检查与本地事务。</li>
     */
    private Duration lockLeaseTime = Duration.ofSeconds(60);

    /** 启动时拒绝负等待与无法传给模板的毫秒范围。 */
    @AssertTrue(message = "data-query.management 锁等待必须为非负时长，租约至少 1ms，且均不超过 Integer.MAX_VALUE 毫秒")
    public boolean isLockTimingValid() {
        Duration maximum = Duration.ofMillis(Integer.MAX_VALUE);
        return lockWaitTime != null && lockLeaseTime != null && !lockWaitTime.isNegative()
            && lockWaitTime.compareTo(maximum) <= 0 && lockLeaseTime.compareTo(Duration.ofMillis(1)) >= 0
            && lockLeaseTime.compareTo(maximum) <= 0;
    }

    /**
     * TRY_LOCK 等待毫秒数。
     *
     * @return 毫秒
     */
    public long lockWaitMillis() {
        return lockWaitTime.toMillis();
    }

    /**
     * 锁租约毫秒数。
     *
     * @return 毫秒
     */
    public long lockLeaseMillis() {
        return lockLeaseTime.toMillis();
    }
}
