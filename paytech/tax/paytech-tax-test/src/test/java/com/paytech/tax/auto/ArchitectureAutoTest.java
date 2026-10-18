package com.paytech.tax.auto;

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
 * 对 tax-service 的四个模块执行共享规则 R1–R12（design/02）；类从各模块 {@code target/classes} 导入
 * <li>搬迁前即存在、本步不改合同而保留的偏差登记在 arch-rules 的 {@code known-deviations.yaml}：新增违规失败；
 * 登记中属于本服务的条目必须仍然出现，偏差消除后同步删除登记。</li>
 *
 * @see ArchitectureRules#checkService(String, java.util.Collection)
 */
class ArchitectureAutoTest {

    private static final String SERVICE = "tax";

    private static final String MODULE_PREFIX = "com.paytech:paytech-" + SERVICE + "-";

    private static final List<String> MODULES = List.of("paytech-tax-api", "paytech-tax-core",
        "paytech-tax-app", "paytech-tax-test");

    /** 除登记的既有偏差外零违规；违规清单逐条输出，便于定位模块与类。 */
    @Test
    void test_allRulesPass() {
        Path serviceRoot = Path.of(System.getProperty("tax.reactor.root", "..")).toAbsolutePath().normalize();
        List<Path> moduleRoots = MODULES.stream().map(serviceRoot::resolve).toList();
        for (Path moduleRoot : moduleRoots) {
            assertTrue(Files.isRegularFile(moduleRoot.resolve("pom.xml")), "模块不存在: " + moduleRoot);
        }
        assertTrue(Files.isDirectory(serviceRoot.resolve("paytech-tax-core/target/classes")),
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
