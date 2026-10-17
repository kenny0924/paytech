package com.paytech.arch.auto;

import com.paytech.arch.rule.CompensationHandlerRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R12：补偿 Handler 只能在 core，eventType 必须登记在服务事件目录。
 *
 * @see com.paytech.arch.rule.CompensationHandlerRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class CompensationHandlerRuleAutoTest {
    private static final String HANDLER = Fixtures.classes("r12.CompensationEventHandler");

    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12/pass/ledger/paytech-ledger-core"), Fixtures.classes("r12.pass.ledgercore"))
            .module(Fixtures.path("r12/pass/ledger/paytech-ledger-test"), Fixtures.classes("r12.pass.ledgertest"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER).check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12/violation/ledger/paytech-ledger-core"),
                Fixtures.classes("r12.violation.ledgercore"))
            .module(Fixtures.path("r12/violation/ledger/paytech-ledger-app"),
                Fixtures.classes("r12.violation.ledgerapp"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER).check(scope))
            .allFrom("R12")
            .count(3)
            .contains("UnregisteredHandler", "LEDGER_UNREGISTERED 未在事件目录")
            .contains("EnumCodeHandler", "编译期常量")
            .contains("AppRegisteredHandler", "只能注册在 -core");
    }

    @Test
    void test_noDataFound() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12/missing-catalog/ledger/paytech-ledger-core"),
                Fixtures.classes("r12.pass.ledgercore"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER).check(scope))
            .allFrom("R12")
            .contains("compensation-event-catalog.yaml", "缺少事件目录文件")
            .contains("ProjectionRebuildHandler", "未在事件目录");
    }
}
