package com.paytech.gateway.auto.architecture;

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
 * 对 api-gateway 的 app 与 test 两个模块执行共享规则 R1–R12（design/02）；类从各模块 {@code target/classes} 导入。
 * <li>Gateway 无 api / core（design/03 §1.2）：R1、R3、R4、R5 没有 core 检查对象；app 对其他服务 core / app 的隔离由 R6
 * 依赖图与聚合 POM 的 Enforcer 共同约束。</li>
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 * @author Claude
 */
class ArchitectureAutoTest {

    private static final String SERVICE = "api-gateway";

    private static final List<String> MODULES = List.of("paytech-api-gateway-app", "paytech-api-gateway-test");

    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of(System.getProperty("paytech.api-gateway.root", "..")).toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-api-gateway-app/target/classes")),
            "app 尚未编译，规则无类可检: 先在 paytech/ 执行 ./mvnw verify");

        List<RuleViolation> violations = ArchitectureRules.checkService(SERVICE, moduleRoots);

        assertEquals(List.of(), violations, () -> violations.stream().map(RuleViolation::toString)
            .collect(Collectors.joining(System.lineSeparator())));
    }
}
