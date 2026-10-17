package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.RuleViolation;

/**
 * 对 operations-console-bff 的 app 与 test 模块执行共享规则 R1–R12（design/02）；BFF 无 api 与 core 模块。
 * 服务目录取 Surefire 工作目录（本测试模块）的上一级，不按目录名向上查找。
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 * @author Claude
 */
class ArchitectureAutoTest {

    private static final String SERVICE = "operations-console-bff";

    private static final List<String> MODULES = List.of("paytech-operations-console-bff-app",
        "paytech-operations-console-bff-test");

    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of("").resolve("..").toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-operations-console-bff-app/target/classes")),
            "app 尚未编译，规则无类可检: 先在 paytech/ 执行 ./mvnw verify");

        List<RuleViolation> violations = ArchitectureRules.checkService(SERVICE, moduleRoots);

        assertEquals(List.of(), violations, () -> violations.stream().map(RuleViolation::toString)
            .collect(Collectors.joining(System.lineSeparator())));
    }
}
