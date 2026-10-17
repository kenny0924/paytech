package com.paytech.arch.reactor.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.pom.PomReader;
import com.paytech.arch.rule.RuleViolation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Reactor 级依赖图校验：读取 {@code paytech/pom.xml} 当前列出的全部模块，执行 R1、R2、R3、R6、R8 的依赖图部分。
 *
 * <p>
 * 真实 Reactor 中的服务模块必须零违规；另以真实 Reactor 模型加注入的违规模块证明规则在该模型上确实生效，避免空断言。
 *
 * @see com.paytech.arch.ArchitectureRules#checkReactorDependencyGraph(Path)
 * @author Claude
 */
class ReactorArchitectureAutoTest {

    private static final Path REACTOR_ROOT = Path.of(System.getProperty("paytech.reactor.root", "../.."))
        .toAbsolutePath().normalize();

    @Test
    void test_normalFlow() {
        Assertions.assertTrue(Files.isRegularFile(REACTOR_ROOT.resolve("pom.xml")), "未找到 Reactor: " + REACTOR_ROOT);
        List<MavenModule> modules = new PomReader().readReactor(REACTOR_ROOT);

        Assertions.assertEquals("paytech", modules.getFirst().artifactId());
        List<String> artifactIds = modules.stream().map(MavenModule::artifactId).toList();
        Assertions.assertTrue(artifactIds.containsAll(List.of("paytech-dependencies", "paytech-parent",
            "paytech-arch-rules", "paytech-instance-manifest", "paytech-arch-rules-reactor-test")),
            () -> "Reactor 模块不完整: " + artifactIds);
        // 聚合 POM 可以嵌套（服务聚合 POM 再列出四个模块），因此按全部聚合 POM 声明的模块总数核对。
        int declaredModules = modules.stream().mapToInt(module -> module.moduleNames().size()).sum();
        Assertions.assertEquals(declaredModules + 1, modules.size(),
            () -> "Reactor 模块数与各聚合 POM 声明不一致: " + artifactIds);
        List<RuleViolation> violations = ArchitectureRules.checkReactorDependencyGraph(REACTOR_ROOT);
        Assertions.assertTrue(violations.isEmpty(), () -> "Reactor 依赖图违规:\n" + describe(violations));
    }

    @Test
    void test_businessRuleViolation() {
        List<MavenModule> modules = new ArrayList<>(new PomReader().readReactor(REACTOR_ROOT));
        modules.add(new MavenModule(REACTOR_ROOT.resolve("ledger/paytech-ledger-core"), "com.paytech",
            "paytech-ledger-core", "jar", List.of(
                new MavenDependency("com.paytech", "paytech-payment-core", "compile"),
                new MavenDependency("com.paytech", "paytech-payment-api", "compile"),
                new MavenDependency("com.paytech", "paytech-messaging", "compile")),
            List.of()));
        modules.add(new MavenModule(REACTOR_ROOT.resolve("ledger/paytech-ledger-api"), "com.paytech",
            "paytech-ledger-api", "jar", List.of(new MavenDependency("com.paytech", "paytech-mvc-web-starter",
                "compile")), List.of()));
        modules.add(new MavenModule(REACTOR_ROOT.resolve("ledger/paytech-ledger-app"), "com.paytech",
            "paytech-ledger-app", "jar", List.of(new MavenDependency("com.paytech", "paytech-payment-app",
                "runtime")), List.of()));
        Assertions.assertEquals(ModuleRole.CORE, modules.get(modules.size() - 3).identity().role());

        List<RuleViolation> violations = ArchitectureRules.checkDependencyGraph(modules);

        Assertions.assertEquals(List.of("R1", "R2", "R3", "R6", "R8"),
            violations.stream().map(RuleViolation::ruleId).distinct().sorted(this::compareRuleIds).toList(),
            () -> describe(violations));
    }

    private int compareRuleIds(String left, String right) {
        return Integer.compare(Integer.parseInt(left.substring(1)), Integer.parseInt(right.substring(1)));
    }

    private static String describe(List<RuleViolation> violations) {
        return violations.stream().map(RuleViolation::toString).collect(Collectors.joining("\n"));
    }
}
