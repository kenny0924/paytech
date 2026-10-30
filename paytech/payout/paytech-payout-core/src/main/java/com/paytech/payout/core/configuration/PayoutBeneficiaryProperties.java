package com.paytech.payout.core.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 收款账户核验与冷却配置（B4、Q-3）。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("payout.beneficiary")
public class PayoutBeneficiaryProperties {
    /**
     * 核验通过后的冷却时长
     * <li>默认 24 小时，Client 与 Local Partner 统一，不按金额免冷却（Q-3）。</li>
     * <li>批准时冻结为 cooling_until = 决定时间 + 本值；修改只影响之后的批准，已冻结的冷却结束时间不重算。</li>
     */
    @NotNull
    private Duration cooling = Duration.ofHours(24);
}
