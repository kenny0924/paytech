package com.paytech.platform.support.filedocument.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/** 文件存储快照刷新与存储管理写入的锁参数；存储实例、桶与路由本身在数据库中维护，不在配置文件中。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "files.storage")
public class FileStorageProperties {

    /**
     * 进程内存储快照的定时刷新间隔
     * <li>默认 30s；本进程的管理写入提交后立即刷新，其他实例最迟在此间隔后看到变化。只在 files.enabled=true 时调度。</li>
     */
    private Duration snapshotRefresh = Duration.ofSeconds(30);

    /**
     * 存储管理写入的 TRY_LOCK 最长等待时间
     * <li>默认 500ms，0 表示立即尝试；超时返回 409。修改需重启。</li>
     */
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 存储管理写入的资源锁租约
     * <li>默认 30s，需覆盖 Vault 与存储检查耗时。修改需重启。</li>
     */
    private Duration lockLeaseTime = Duration.ofSeconds(30);

    /** 启动时拒绝非正刷新间隔、负等待及无法传给模板的毫秒范围。 */
    @AssertTrue(message = "files.storage 刷新间隔与锁时长必须为正，等待可为 0，且均不超过 Integer.MAX_VALUE 毫秒")
    public boolean isTimingValid() {
        Duration maximum = Duration.ofMillis(Integer.MAX_VALUE);
        return snapshotRefresh != null && lockWaitTime != null && lockLeaseTime != null
            && snapshotRefresh.compareTo(Duration.ofSeconds(1)) >= 0 && !lockWaitTime.isNegative()
            && lockWaitTime.compareTo(maximum) <= 0 && lockLeaseTime.compareTo(Duration.ofMillis(1)) >= 0
            && lockLeaseTime.compareTo(maximum) <= 0;
    }
}
