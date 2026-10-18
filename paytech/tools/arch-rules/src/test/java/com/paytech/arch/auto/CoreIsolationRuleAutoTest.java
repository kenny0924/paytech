package com.paytech.arch.auto;

import com.paytech.arch.rule.CoreIsolationRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R1 import 部分：core 类不得引用其他服务 core / app 模块的类。
 *
 * @see com.paytech.arch.rule.CoreIsolationRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class CoreIsolationRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r1.pass.ledgercore"))
            .module(Fixtures.module("payment", "api"), Fixtures.classes("r1.pass.paymentapi"))
            .build();

        ViolationChecker.of(new CoreIsolationRule().check(scope)).none();
    }

    /** 同一服务的多个 core 模块（如 Platform 的业务模块与应用编排层）可以互相依赖：依赖图与 import 都不违规。 */
    @Test
    void test_sameServiceCoreModules() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "posting-core"), Fixtures.classes("r1.samesvc.postingcore"))
            .module(Fixtures.module("ledger", "journal-core"), Fixtures.classes("r1.samesvc.journalcore"))
            .build();

        ViolationChecker.of(new CoreIsolationRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r1.violation.ledgercore"))
            .module(Fixtures.module("payment", "core"), Fixtures.classes("r1.violation.paymentcore"))
            .module(Fixtures.module("payment", "app"), Fixtures.classes("r1.violation.paymentapp"))
            .build();

        ViolationChecker.of(new CoreIsolationRule().check(scope))
            .allFrom("R1")
            .containsMessage("LedgerPostingService", "PaymentExecutionService")
            .containsMessage("LedgerPostingService", "PaymentApplication");
    }
}
