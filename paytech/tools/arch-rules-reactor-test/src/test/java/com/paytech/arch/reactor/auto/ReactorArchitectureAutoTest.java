package com.paytech.arch.reactor.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.pom.PomReader;
import com.paytech.arch.rule.CompensationHandlerRule;
import com.paytech.arch.rule.RuleViolation;
import com.paytech.arch.support.KnownDeviations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Reactor 级依赖图校验：读取 {@code paytech/pom.xml} 当前列出的全部模块（含 {@code foundation/} 下的嵌套模块），执行
 * R1、R2、R3、R6、R8、R13、R15 的依赖图部分。
 *
 * <p>
 * 真实 Reactor 中的服务模块除 {@code known-deviations.yaml} 登记的既有偏差外必须零违规；另以真实 Reactor 模型加注入的违规模块证明规则在该模型上确实生效，避免空断言；R8 同时以真实
 * Foundation 坐标核对：{@code -core} 依赖 {@code paytech-messaging-api} 合法，依赖 {@code paytech-messaging} 违规。
 *
 * @see com.paytech.arch.ArchitectureRules#checkReactorDependencyGraph(Path)
 * @author Claude
 */
class ReactorArchitectureAutoTest {

    private static final List<String> FOUNDATION_ARTIFACTS = List.of("paytech-foundation", "paytech-common-core",
        "paytech-http-problem", "paytech-json-canonicalization", "paytech-http-client-support", "paytech-test-support",
        "paytech-mybatis-support", "paytech-command-idempotency", "paytech-snowflake-id", "paytech-mvc-web-starter",
        "paytech-caffeine-starter", "paytech-redis-starter", "paytech-xxl-job-starter", "paytech-compensation-event",
        "paytech-messaging-api", "paytech-messaging", "paytech-internal-auth");

    /** 依赖图部分覆盖的规则；登记偏差中属于这些规则的条目必须仍然出现。 */
    private static final Set<String> GRAPH_RULES = Set.of("R1", "R2", "R3", "R6", "R8", "R15");

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
        Assertions.assertTrue(artifactIds.containsAll(FOUNDATION_ARTIFACTS),
            () -> "Foundation 模块不完整: " + artifactIds);
        Assertions.assertEquals(declaredModuleCount(modules.getFirst(), modules) + 1, modules.size(),
            () -> "Reactor 模块数与 paytech/pom.xml 的嵌套声明不一致: " + artifactIds);
        Assertions.assertTrue(modules.stream().filter(module -> FOUNDATION_ARTIFACTS.contains(module.artifactId()))
            .allMatch(module -> module.identity().role() == ModuleRole.FOUNDATION),
            () -> "Foundation 模块归类错误: " + artifactIds);
        Assertions.assertTrue(Files.isRegularFile(REACTOR_ROOT.resolve(CompensationHandlerRule.MESSAGING_CATALOG)),
            "R12 读取的 MQ 事件目录不存在: " + CompensationHandlerRule.MESSAGING_CATALOG);
        List<RuleViolation> violations = ArchitectureRules.checkReactorDependencyGraph(REACTOR_ROOT);
        KnownDeviations known = KnownDeviations.loadDefault();
        List<RuleViolation> unregistered = known.unregistered(violations);
        Assertions.assertTrue(unregistered.isEmpty(), () -> "Reactor 依赖图违规:\n" + describe(unregistered));
        // 依赖图只产出以 Maven 坐标为主体的违规；同一规则编号下的类级（ArchUnit "classes"）登记由各服务 ArchitectureAutoTest 核对。
        List<RuleViolation> stale = known.missing(violations,
            entry -> GRAPH_RULES.contains(entry.ruleId()) && entry.subject().startsWith("com.paytech:"));
        Assertions.assertTrue(stale.isEmpty(), () -> "已消除的偏差仍在登记中:\n" + describe(stale));
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

    @Test
    void test_messagingApiDependency() {
        List<MavenModule> modules = new ArrayList<>(new PomReader().readReactor(REACTOR_ROOT));
        MavenModule messagingApi = modules.stream().filter(module -> "paytech-messaging-api".equals(module.artifactId()))
            .findFirst().orElseThrow();
        modules.add(new MavenModule(REACTOR_ROOT.resolve("billing/paytech-billing-core"), "com.paytech",
            "paytech-billing-core", "jar", List.of(new MavenDependency(messagingApi.groupId(),
                messagingApi.artifactId(), "compile")), List.of()));
        KnownDeviations known = KnownDeviations.loadDefault();
        Assertions.assertTrue(known.unregistered(ArchitectureRules.checkDependencyGraph(modules)).isEmpty(),
            "-core 依赖 paytech-messaging-api 应合法");

        modules.add(new MavenModule(REACTOR_ROOT.resolve("account/paytech-account-core"), "com.paytech",
            "paytech-account-core", "jar", List.of(new MavenDependency("com.paytech", "paytech-messaging", "compile")),
            List.of()));
        List<RuleViolation> violations = known.unregistered(ArchitectureRules.checkDependencyGraph(modules));
        Assertions.assertEquals(List.of("R8"), violations.stream().map(RuleViolation::ruleId).distinct().toList(),
            () -> describe(violations));
    }

    /** R15：真实 Reactor 中外部 SDK 只在例外模块；注入一个依赖 AWS SDK 的 core 后规则生效，-test 模块不计。 */
    @Test
    void test_externalSdkDependency() {
        List<MavenModule> modules = new ArrayList<>(new PomReader().readReactor(REACTOR_ROOT));
        KnownDeviations known = KnownDeviations.loadDefault();
        Assertions.assertTrue(known.unregistered(ArchitectureRules.checkDependencyGraph(modules)).stream()
            .noneMatch(violation -> "R15".equals(violation.ruleId())), "真实 Reactor 不应有 R15 违规");

        modules.add(new MavenModule(REACTOR_ROOT.resolve("ledger/paytech-ledger-test"), "com.paytech",
            "paytech-ledger-test", "jar", List.of(new MavenDependency("io.minio", "minio", "compile")), List.of()));
        modules.add(new MavenModule(REACTOR_ROOT.resolve("billing/paytech-billing-core"), "com.paytech",
            "paytech-billing-core", "jar", List.of(new MavenDependency("software.amazon.awssdk", "s3", "compile")),
            List.of()));
        List<RuleViolation> violations = known.unregistered(ArchitectureRules.checkDependencyGraph(modules));
        Assertions.assertEquals(List.of("R15"), violations.stream().map(RuleViolation::ruleId).distinct().toList(),
            () -> describe(violations));
        Assertions.assertEquals(1, violations.size(), () -> describe(violations));
    }

    private static int declaredModuleCount(MavenModule aggregator, List<MavenModule> modules) {
        int count = 0;
        for (String child : aggregator.moduleNames()) {
            Path childRoot = aggregator.root().resolve(child).normalize();
            MavenModule childModule = modules.stream().filter(module -> module.root().equals(childRoot)).findFirst()
                .orElseThrow(() -> new AssertionError("未读取到声明的模块: " + childRoot));
            count += 1 + declaredModuleCount(childModule, modules);
        }
        return count;
    }

    private int compareRuleIds(String left, String right) {
        return Integer.compare(Integer.parseInt(left.substring(1)), Integer.parseInt(right.substring(1)));
    }

    private static String describe(List<RuleViolation> violations) {
        return violations.stream().map(RuleViolation::toString).collect(Collectors.joining("\n"));
    }
}
