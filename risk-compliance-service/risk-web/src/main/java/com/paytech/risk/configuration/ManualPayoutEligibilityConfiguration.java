package com.paytech.risk.configuration;

import com.paytech.risk.decision.domainservice.service.BootstrapManualPayoutEligibilityService;
import com.paytech.risk.decision.domainservice.service.ManualPayoutEligibilityService;
import com.paytech.risk.decision.domainservice.service.UnavailableManualPayoutEligibilityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Manual Payout 资格评估实现选择。
 */
@Configuration(proxyBeanMethods = false)
public class ManualPayoutEligibilityConfiguration {

    @Bean
    @Profile("local-evaluation-bootstrap")
    ManualPayoutEligibilityService bootstrapManualPayoutEligibilityService() {
        return new BootstrapManualPayoutEligibilityService();
    }

    @Bean
    @Profile("!local-evaluation-bootstrap")
    ManualPayoutEligibilityService unavailableManualPayoutEligibilityService() {
        return new UnavailableManualPayoutEligibilityService();
    }
}
