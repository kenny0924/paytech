package com.paytech.arch.auto;

import com.paytech.arch.rule.CoreWebBootstrapRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R5：core 不得声明启动类、Controller 或请求映射；MQ 监听允许。
 *
 * @see com.paytech.arch.rule.CoreWebBootstrapRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class CoreWebBootstrapRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r5.pass.ledgercore"))
            .module(Fixtures.module("ledger", "app"), Fixtures.classes("r5.pass.ledgerapp"))
            .build();

        ViolationChecker.of(new CoreWebBootstrapRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r5.violation.ledgercore"))
            .build();

        ViolationChecker.of(new CoreWebBootstrapRule().check(scope))
            .allFrom("R5")
            .containsMessage("LedgerApplication", "SpringBootApplication")
            .containsMessage("LedgerController", "Controller")
            .containsMessage("LedgerQueryEndpoint.entries()", "RequestMapping");
    }
}
