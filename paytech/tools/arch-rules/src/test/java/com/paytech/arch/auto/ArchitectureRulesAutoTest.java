package com.paytech.arch.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.ArchitectureRule;
import com.paytech.arch.rule.DependencyGraphRule;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 规则集入口：R1–R12、R14 与 R15 全部登记，依赖图另含 R13、R16，服务级检查聚合各规则结果。
 *
 * @see com.paytech.arch.ArchitectureRules#checkService(String, java.util.Collection)
 * @author Claude
 */
class ArchitectureRulesAutoTest {

    @Test
    void test_normalFlow() {
        Assertions.assertEquals(List.of("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10", "R11", "R12", "R14",
            "R15"),
            ArchitectureRules.all().stream().map(ArchitectureRule::id).toList());
        Assertions.assertEquals(List.of("R1", "R2", "R3", "R6", "R8", "R13", "R15", "R16"),
            ArchitectureRules.dependencyGraphRules().stream().map(DependencyGraphRule::id).toList());
        Path service = Fixtures.path("r4/pass/ledger");

        ViolationChecker.of(ArchitectureRules.checkService("ledger",
            List.of(service.resolve("paytech-ledger-core"), service.resolve("paytech-ledger-app")))).none();
    }

    @Test
    void test_businessRuleViolation() {
        Path service = Fixtures.path("r10/violation/ledger");

        ViolationChecker.of(ArchitectureRules.checkService("ledger", List.of(service.resolve("paytech-ledger-core"))))
            .allFrom("R10")
            .count(3);
    }
}
