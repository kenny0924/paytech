package com.paytech.risk.auto.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.RuleViolation;
import com.paytech.arch.support.KnownDeviations;

/**
 * 对 risk-decision 的四个模块执行共享规则 R1–R12（design/02）；类从各模块 {@code target/classes} 导入。
 * <p>
 * 登记在 arch-rules {@code known-deviations.yaml} 中属于本服务的既有偏差必须仍然出现；新增违规失败。
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 */
class ArchitectureAutoTest {
    private static final String SERVICE = "risk-decision";

    private static final String MODULE_PREFIX = "com.paytech:paytech-" + SERVICE + "-";

    private static final List<String> MODULES = List.of("paytech-risk-decision-api", "paytech-risk-decision-core",
        "paytech-risk-decision-app", "paytech-risk-decision-test");

    /** 除登记的既有偏差外零违规；违规清单逐条输出。 */
    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of(System.getProperty("risk.reactor.root", "..")).toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-risk-decision-core/target/classes")),
            "core 尚未编译，规则无类可检: 先在 paytech/ 执行 ./mvnw verify");

        List<RuleViolation> violations = ArchitectureRules.checkService(SERVICE, moduleRoots);

        KnownDeviations known = KnownDeviations.loadDefault();
        assertEquals(List.of(), known.unregistered(violations), () -> describe(known.unregistered(violations)));
        assertEquals(List.of(), known.missing(violations, entry -> entry.subject().startsWith(MODULE_PREFIX)),
            "已消除的偏差仍在 known-deviations.yaml 中登记");
    }

    /** 逐行输出违规。 */
    private static String describe(List<RuleViolation> violations) {
        return violations.stream().map(RuleViolation::toString)
            .collect(Collectors.joining(System.lineSeparator(), "未登记的违规：" + System.lineSeparator(), ""));
    }
}
