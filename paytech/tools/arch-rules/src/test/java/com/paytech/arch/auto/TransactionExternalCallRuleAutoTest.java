package com.paytech.arch.auto;

import com.paytech.arch.rule.TransactionExternalCallRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R7：事务类不得直接注入 api HTTP 接口、VaultClient、RabbitTemplate。
 *
 * @see com.paytech.arch.rule.TransactionExternalCallRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class TransactionExternalCallRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r7.pass.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r7.pass.paymentapi"))
            .build();

        ViolationChecker.of(new TransactionExternalCallRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r7.violation.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r7.violation.paymentapi"))
            .module(Fixtures.module("vault", "client"), Fixtures.classes("r7.violation.vaultclient"))
            .build();

        ViolationChecker.of(new TransactionExternalCallRule().check(scope))
            .allFrom("R7")
            .count(4)
            .containsMessage("LedgerPostingService", "HTTP 接口", "PaymentQueryApi")
            .containsMessage("LedgerPublishService", "MQ 发送模板", "RabbitTemplate")
            .containsMessage("LedgerRevealService", "Vault 客户端", "VaultClient")
            .containsMessage("LedgerRevealService", "HTTP 接口", "ExchangeRateClient");
    }
}
