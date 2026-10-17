package com.paytech.arch.auto;

import com.paytech.arch.rule.CompensationHandlerRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R12 的领域事件部分：{@code @DomainEventHandler} 只能在 core，eventType 必须登记在 MQ 事件目录且 consumers 含本服务。
 *
 * @see com.paytech.arch.rule.CompensationHandlerRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class DomainEventHandlerCatalogRuleAutoTest {

    private static final String HANDLER = Fixtures.classes("r12.CompensationEventHandler");

    private static final String ANNOTATION = Fixtures.classes("r12domain.DomainEventHandler");

    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12domain/ledger/paytech-ledger-core"), Fixtures.classes("r12domain.pass.ledgercore"))
            .module(Fixtures.path("r12domain/ledger/paytech-ledger-test"), Fixtures.classes("r12domain.pass.ledgertest"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER, ANNOTATION).check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12domain/ledger/paytech-ledger-core"),
                Fixtures.classes("r12domain.violation.ledgercore"))
            .module(Fixtures.path("r12domain/ledger/paytech-ledger-app"),
                Fixtures.classes("r12domain.violation.ledgerapp"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER, ANNOTATION).check(scope))
            .allFrom("R12")
            .count(3)
            .contains("RefundPostingHandler", "PaymentRefunded 未在事件目录")
            .contains("BalanceEchoHandler", "consumers 未包含 ledger")
            .contains("AppFactHandler", "只能注册在 -core");
    }

    @Test
    void test_noDataFound() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.path("r12/pass/ledger/paytech-ledger-core"), Fixtures.classes("r12domain.pass.ledgercore"))
            .build();

        ViolationChecker.of(new CompensationHandlerRule(HANDLER, ANNOTATION).check(scope))
            .allFrom("R12")
            .contains("events.yaml", "缺少 MQ 事件目录")
            .contains("EconomicFactPostingHandler", "未在事件目录");
    }
}
