package com.paytech.arch.auto;

import com.paytech.arch.rule.ProcessorTemplateRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;

import org.junit.jupiter.api.Test;

/**
 * R14：服务 {@code -core} 的 {@code service.processor} 包中具体 {@code *Processor} 必须继承 Foundation 模板；BFF 不在范围。
 *
 * @see com.paytech.arch.rule.ProcessorTemplateRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class ProcessorTemplateRuleAutoTest {

    private static final ProcessorTemplateRule RULE =
        new ProcessorTemplateRule(Fixtures.classes("r14.AbstractProcessor"));

    /** 直接与经资源锁模板间接继承均通过；抽象类、非 Processor 命名、其他包与 -app 模块不检查。 */
    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r14.pass.ledgercore"))
            .module(Fixtures.module("ledger", "app"), Fixtures.classes("r14.pass.ledgerapp"))
            .build();

        ViolationChecker.of(RULE.check(scope)).none();
    }

    /** 未继承模板、或只继承未挂模板的抽象类的具体 Processor 各报告一条。 */
    @Test
    void test_businessRuleViolation() {
        ServiceArchitectureScope scope = Fixtures.scope("ledger")
            .module(Fixtures.module("ledger", "core"), Fixtures.classes("r14.violation.ledgercore"))
            .build();

        ViolationChecker.of(RULE.check(scope))
            .allFrom("R14")
            .count(2)
            .contains("CloseBookProcessor", "AbstractProcessor")
            .contains("LedgerQueryProcessor", "AbstractProcessor");
    }

    /** BFF 的聚合 Processor 即使位于 core 形态的模块也不检查。 */
    @Test
    void test_edgeCase() {
        ServiceArchitectureScope scope = Fixtures.scope("client-console-bff")
            .module(Fixtures.path("r14/client-console-bff/paytech-client-console-bff-core"),
                Fixtures.classes("r14.bff.bffcore"))
            .build();

        ViolationChecker.of(RULE.check(scope)).none();
    }
}
