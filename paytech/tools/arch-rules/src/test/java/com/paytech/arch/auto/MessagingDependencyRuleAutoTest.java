package com.paytech.arch.auto;

import com.paytech.arch.rule.MessagingDependencyRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R8 import 部分：core 只能引用 messaging-api，发布端实现只在 app。
 *
 * @see com.paytech.arch.rule.MessagingDependencyRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class MessagingDependencyRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r8.pass.ledgercore"))
            .module(Fixtures.module("ledger", "app"), Fixtures.classes("r8.pass.ledgerapp"))
            .module(Fixtures.foundation("paytech-messaging"), Fixtures.classes("r8.pass.messaging"))
            .module(Fixtures.foundation("paytech-messaging-api"), Fixtures.classes("r8.pass.messagingapi"))
            .build();

        ViolationChecker.of(new MessagingDependencyRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r8.violation.ledgercore"))
            .module(Fixtures.foundation("paytech-messaging"), Fixtures.classes("r8.violation.messaging"))
            .build();

        ViolationChecker.of(new MessagingDependencyRule().check(scope))
            .allFrom("R8")
            .containsMessage("LedgerEventService", "RabbitEventPublisher");
    }
}
