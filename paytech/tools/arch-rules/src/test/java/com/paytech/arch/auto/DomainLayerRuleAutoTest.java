package com.paytech.arch.auto;

import com.paytech.arch.rule.DomainLayerRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R3 import 部分：领域层 core 不得引用流程层 api。
 *
 * @see com.paytech.arch.rule.DomainLayerRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class DomainLayerRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r3.pass.ledgercore"))
            .module(Fixtures.module("billing", "api"), Fixtures.classes("r3.pass.billingapi"))
            .build();

        ViolationChecker.of(new DomainLayerRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r3.violation.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r3.violation.paymentapi"))
            .build();

        ViolationChecker.of(new DomainLayerRule().check(scope))
            .allFrom("R3")
            .containsMessage("LedgerAutoPostingService", "PaymentQueryApi");
    }

    @Test
    void test_edgeCase() {
        // 流程层服务自身引用流程层 api 不受 R3 约束。
        ServiceArchitectureScope scope = Fixtures.scope("payment")
            .module(Fixtures.module("payment", "core"), Fixtures.classes("r3.violation.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r3.violation.paymentapi"))
            .build();

        ViolationChecker.of(new DomainLayerRule().check(scope)).none();
    }
}
