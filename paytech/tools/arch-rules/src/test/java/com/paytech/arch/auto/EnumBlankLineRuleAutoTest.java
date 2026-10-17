package com.paytech.arch.auto;

import com.paytech.arch.rule.EnumBlankLineRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ModuleFiles;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R10：相邻枚举常量之间恰好一个空行。
 *
 * @see com.paytech.arch.rule.EnumBlankLineRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class EnumBlankLineRuleAutoTest {
    @Test
    void test_normalFlow() {
        Path core = Fixtures.path("r10/pass/ledger/paytech-ledger-core");

        ViolationChecker.of(new EnumBlankLineRule().check(ServiceArchitectureScope.of("ledger", List.of(core)))).none();
    }

    @Test
    void test_businessRuleViolation() {
        Path core = Fixtures.path("r10/violation/ledger/paytech-ledger-core");

        ViolationChecker.of(new EnumBlankLineRule().check(ServiceArchitectureScope.of("ledger", List.of(core))))
            .allFrom("R10")
            .count(3)
            .contains("JournalDirection.java:4", "缺少空行")
            .contains("JournalDirection.java:5", "缺少空行")
            .contains("JournalDirection.java:8", "空行多于一行");
    }

    @Test
    void test_edgeCase() {
        // 规则库自身的生产源码同样遵守 R10（Surefire 工作目录为模块根目录）。
        List<Path> sources = ModuleFiles.list(Path.of(""), "src/main/java", ".java");

        org.junit.jupiter.api.Assertions.assertFalse(sources.isEmpty(), "未找到规则库源码");
        ViolationChecker.of(new EnumBlankLineRule().checkFiles(sources)).none();
    }
}
