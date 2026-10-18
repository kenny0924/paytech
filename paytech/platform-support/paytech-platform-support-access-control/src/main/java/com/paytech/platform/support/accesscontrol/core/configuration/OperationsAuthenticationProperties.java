package com.paytech.platform.support.accesscontrol.core.configuration;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/** Operations 认证及人员角色管理共用的锁配置；模板锁由同资源写入口共享，不读取注解锁配置。 */
@Getter
@Setter
@Validated
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
@ConfigurationProperties(prefix = "operations.authentication")
public class OperationsAuthenticationProperties {
    /** TRY_LOCK 最长等待时间，默认 500ms，0 表示立即尝试。 */
    private Duration lockWaitTime = Duration.ofMillis(500);

    /** 资源锁显式租约，默认 30s；数据库唯一约束和 CAS 仍为最终保护。 */
    private Duration lockLeaseTime = Duration.ofSeconds(30);

    /** 启动时拒绝空值、负等待及无法传给模板的毫秒范围，最短租约为 1ms。 */
    @AssertTrue(message = "Operations 认证锁等待必须为非负时长，租约至少 1ms，且均不超过 Integer.MAX_VALUE 毫秒")
    public boolean isLockTimingValid() {
        Duration maximum = Duration.ofMillis(Integer.MAX_VALUE);
        return lockWaitTime != null && lockLeaseTime != null && !lockWaitTime.isNegative()
            && lockWaitTime.compareTo(maximum) <= 0 && lockLeaseTime.compareTo(Duration.ofMillis(1)) >= 0
            && lockLeaseTime.compareTo(maximum) <= 0;
    }
}
