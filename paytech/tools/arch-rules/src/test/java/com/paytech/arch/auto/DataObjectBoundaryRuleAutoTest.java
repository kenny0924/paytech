package com.paytech.arch.auto;

import com.paytech.arch.rule.DataObjectBoundaryRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import org.junit.jupiter.api.Test;

/**
 * R9：DataObject 不得出现在 api，也不得被其他服务引用。
 *
 * @see com.paytech.arch.rule.DataObjectBoundaryRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class DataObjectBoundaryRuleAutoTest {
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r9.pass.ledgercore"))
            .build();

        ViolationChecker.of(new DataObjectBoundaryRule().check(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "api"), Fixtures.classes("r9.violation.ledgerapi"))
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r9.violation.ledgercore"))
            .module(Fixtures.module("payment", "core"), Fixtures.classes("r9.violation.paymentcore"))
            .build();

        ViolationChecker.of(new DataObjectBoundaryRule().check(scope))
            .allFrom("R9")
            .containsMessage("ledgerapi.dal.dataobject.JournalEntry")
            .containsMessage("LedgerAccountResponseBody", "LedgerAccount")
            .containsMessage("PaymentIntentReader", "PaymentIntent");
    }
}
