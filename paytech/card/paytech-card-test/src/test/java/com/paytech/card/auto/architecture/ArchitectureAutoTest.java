package com.paytech.card.auto.architecture;

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
 * 对 card 的全部模块执行共享规则 R1–R12（design/02）；类从各模块 {@code target/classes} 导入。
 *
 * <p>
 * card 是领域层服务（D10、PCI）：R11 只豁免 card-core 与 card-app，card-test 与其他服务都不得引用卡表或 import 卡域加密包；R3 禁止 core
 * 依赖流程层 api；PaymentSucceeded Handler 在 core 且登记于事件目录（R12）。
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 */
class ArchitectureAutoTest {

    private static final String SERVICE = "card";

    private static final String MODULE_PREFIX = "com.paytech:paytech-" + SERVICE + "-";

    private static final List<String> MODULES = List.of("paytech-card-api", "paytech-card-core", "paytech-card-app",
        "paytech-card-test");

    /** 除登记的既有偏差外零违规；违规清单逐条输出，便于定位模块与类。 */
    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of("").resolve("..").toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-" + SERVICE + "-core/target/classes")),
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
