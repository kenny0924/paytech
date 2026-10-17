package com.paytech.data.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.RuleViolation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对 data-service 的四个模块执行共享规则 R1–R12（design/02）；类从各模块 {@code target/classes} 导入。
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 * @author Claude
 */
class ArchitectureAutoTest {

    private static final String SERVICE = "data-service";

    private static final List<String> MODULES = List.of("paytech-data-service-api", "paytech-data-service-core",
        "paytech-data-service-app", "paytech-data-service-test");

    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of(System.getProperty("paytech.data-service.root", "..")).toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-data-service-core/target/classes")),
            "core 尚未编译，规则无类可检: 先在 paytech/ 执行 ./mvnw verify");

        List<RuleViolation> violations = ArchitectureRules.checkService(SERVICE, moduleRoots);

        assertEquals(List.of(), violations, () -> violations.stream().map(RuleViolation::toString)
            .collect(Collectors.joining(System.lineSeparator())));
    }
}
