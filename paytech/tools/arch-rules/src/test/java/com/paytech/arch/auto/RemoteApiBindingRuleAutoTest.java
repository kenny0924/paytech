package com.paytech.arch.auto;

import com.paytech.arch.rule.RemoteApiBindingRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R6 类部分：app 不得在本地实现其他服务的 api 接口。
 *
 * @see com.paytech.arch.rule.RemoteApiBindingRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class RemoteApiBindingRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "app"), Fixtures.classes("r6.pass.ledgerapp"))
            .module(Fixtures.module("ledger", "api"), Fixtures.classes("r6.pass.ledgerapi"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r6.pass.paymentapi"))
            .build();

        ViolationChecker.of(new RemoteApiBindingRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "app"), Fixtures.classes("r6.violation.ledgerapp"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r6.violation.paymentapi"))
            .build();

        ViolationChecker.of(new RemoteApiBindingRule().check(scope))
            .allFrom("R6")
            .count(1)
            .containsMessage("LocalPaymentQueryApi", "PaymentQueryApi");
    }
}
