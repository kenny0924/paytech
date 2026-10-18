package com.paytech.arch.auto;

import com.paytech.arch.rule.ApiDependencyRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R2 import 部分：api 类只能引用本 api 与允许的基础制品。
 *
 * @see com.paytech.arch.rule.ApiDependencyRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class ApiDependencyRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "api"), Fixtures.classes("r2.pass.ledgerapi"))
            .build();

        ViolationChecker.of(new ApiDependencyRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "api"), Fixtures.classes("r2.violation.ledgerapi"))
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r2.violation.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r2.violation.paymentapi"))
            .build();

        ViolationChecker.of(new ApiDependencyRule().check(scope))
            .allFrom("R2")
            .containsMessage("LedgerBalanceResponseBody", "LedgerAccount")
            .containsMessage("LedgerBalanceResponseBody", "PaymentView");
    }
}
