package com.paytech.platform.support.referencedata.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

/** 币种规则维护的模板锁参数，由新建、修改与启停共享；版本条件更新与唯一键仍为最终保护。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ps.reference-data.currency-spec")
public class CurrencySpecManagementProperties {

    /**
     * TRY_LOCK 最长等待时间
     * <li>默认 500ms，0 表示立即尝试；超时返回 409。修改需重启。</li>
     */
    private Duration lockWaitTime = Duration.ofMillis(500);

    /**
     * 资源锁显式租约
     * <li>默认 30s。修改需重启。</li>
     */
    private Duration lockLeaseTime = Duration.ofSeconds(30);

    /** 启动时拒绝空值、负等待及无法传给模板的毫秒范围，最短租约为 1ms。 */
    @AssertTrue(message = "币种规则锁等待必须为非负时长，租约至少 1ms，且均不超过 Integer.MAX_VALUE 毫秒")
    public boolean isLockTimingValid() {
        Duration maximum = Duration.ofMillis(Integer.MAX_VALUE);
        return lockWaitTime != null && lockLeaseTime != null && !lockWaitTime.isNegative()
            && lockWaitTime.compareTo(maximum) <= 0 && lockLeaseTime.compareTo(Duration.ofMillis(1)) >= 0
            && lockLeaseTime.compareTo(maximum) <= 0;
    }
}
