package com.paytech.operations.console.bff.test.configuration;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import com.paytech.operations.console.bff.test.simulator.AccountOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.LedgerOwnerSimulator;
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
    public static final ClientReviewOwnerSimulator CLIENT_REVIEW = clientReviewSimulator();
    public static final LedgerOwnerSimulator LEDGER = new LedgerOwnerSimulator();
    public static final AccountOwnerSimulator ACCOUNT = new AccountOwnerSimulator();

    @Bean(destroyMethod = "close")
    LedgerOwnerSimulator ledgerOwnerSimulator() { return LEDGER; }

    @Bean(destroyMethod = "close")
    AccountOwnerSimulator accountOwnerSimulator() { return ACCOUNT; }

    @Bean(destroyMethod = "close")
    ClientReviewOwnerSimulator clientReviewOwnerSimulator() { return CLIENT_REVIEW; }

    private static ClientReviewOwnerSimulator clientReviewSimulator() {
        try {
            return new ClientReviewOwnerSimulator();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动入驻审核 Owner 本地 HTTP Simulator", exception);
        }
    }

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
