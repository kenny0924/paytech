package com.paytech.operations.console.bff.test.configuration;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 同一 HTTP 测试切片共用相互独立的随机 Loopback Platform 与 Risk Simulator。 */
@TestConfiguration(proxyBeanMethods = false)
public class OperationsBffTestConfiguration {
    private static final AtomicLong JOURNAL_SEQUENCE = new AtomicLong();
    public static final PlatformOperationsSimulator PLATFORM = new PlatformOperationsSimulator(JOURNAL_SEQUENCE);
    public static final RiskDecisionConfigurationSimulator RISK = riskSimulator();

    @Bean(destroyMethod = "close")
    PlatformOperationsSimulator platformOperationsSimulator() { return PLATFORM; }

    @Bean(destroyMethod = "close")
    RiskDecisionConfigurationSimulator riskDecisionConfigurationSimulator() { return RISK; }

    private static RiskDecisionConfigurationSimulator riskSimulator() {
        try {
            return new RiskDecisionConfigurationSimulator(JOURNAL_SEQUENCE);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Risk 本地 HTTP Simulator", exception);
        }
    }
}
