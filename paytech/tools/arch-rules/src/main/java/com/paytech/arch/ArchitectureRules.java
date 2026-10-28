package com.paytech.arch;

import com.paytech.arch.model.MavenModule;
import com.paytech.arch.pom.PomReader;
import com.paytech.arch.rule.ApiDependencyRule;
import com.paytech.arch.rule.ArchitectureRule;
import com.paytech.arch.rule.CardDataIsolationRule;
import com.paytech.arch.rule.ClientTestSupportDependencyRule;
import com.paytech.arch.rule.CompensationHandlerRule;
import com.paytech.arch.rule.CoreIsolationRule;
import com.paytech.arch.rule.CoreWebBootstrapRule;
import com.paytech.arch.rule.DataObjectBoundaryRule;
import com.paytech.arch.rule.DependencyGraphRule;
import com.paytech.arch.rule.DomainLayerRule;
import com.paytech.arch.rule.EnumBlankLineRule;
import com.paytech.arch.rule.ExternalEgressRule;
import com.paytech.arch.rule.MapperTableOwnershipRule;
import com.paytech.arch.rule.MessagingDependencyRule;
import com.paytech.arch.rule.ProcessorTemplateRule;
import com.paytech.arch.rule.ReactorCoordinateRule;
import com.paytech.arch.rule.RemoteApiBindingRule;
import com.paytech.arch.rule.RuleViolation;
import com.paytech.arch.rule.TransactionExternalCallRule;
import com.paytech.arch.scope.ServiceArchitectureScope;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * R1–R12、R14、R15 服务级规则与 R13、R16 依赖图规则的入口（design/02；R15 见 2026-10-20 统一对外渠道出口决定；R16 见
 * 2026-10-27 参考数据客户端组件需求）。
 *
 * <p>
 * 服务 {@code -test} 模块的 {@code ArchitectureAutoTest} 调用 {@link #checkService(String, Collection)}；Reactor 级测试调用
 * {@link #checkReactorDependencyGraph(Path)}。返回空列表表示通过。
 *
 * @author Claude
 */
public final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /**
     * 返回全部服务级规则 R1–R12、R14 与 R15。
     *
     * @return 规则
     */
    public static List<ArchitectureRule> all() {
        return List.of(new CoreIsolationRule(), new ApiDependencyRule(), new DomainLayerRule(),
            new MapperTableOwnershipRule(), new CoreWebBootstrapRule(), new RemoteApiBindingRule(),
            new TransactionExternalCallRule(), new MessagingDependencyRule(), new DataObjectBoundaryRule(),
            new EnumBlankLineRule(), new CardDataIsolationRule(), new CompensationHandlerRule(), new ProcessorTemplateRule(),
            new ExternalEgressRule());
    }

    /**
     * 返回含 Maven 依赖图部分的规则 R1、R2、R3、R6、R8、R15，以及只有依赖图部分的 R13、R16。
     *
     * @return 规则
     */
    public static List<DependencyGraphRule> dependencyGraphRules() {
        return List.of(new CoreIsolationRule(), new ApiDependencyRule(), new DomainLayerRule(),
            new RemoteApiBindingRule(), new MessagingDependencyRule(), new ReactorCoordinateRule(),
            new ExternalEgressRule(), new ClientTestSupportDependencyRule());
    }

    /**
     * 对一个服务执行全部规则。
     *
     * @param serviceName 逻辑服务名
     * @param moduleRoots 服务的 Maven 模块根目录（类从 {@code target/classes} 导入）
     * @return 全部违规
     */
    public static List<RuleViolation> checkService(String serviceName, Collection<Path> moduleRoots) {
        ServiceArchitectureScope scope = ServiceArchitectureScope.of(serviceName, moduleRoots);
        List<RuleViolation> violations = new ArrayList<>();
        for (ArchitectureRule rule : all()) {
            violations.addAll(rule.check(scope));
        }
        return violations;
    }

    /**
     * 读取 Reactor 全部模块，对依赖图执行 R1、R2、R3、R6、R8、R13、R15、R16。
     *
     * @param aggregatorRoot 聚合 POM 所在目录（{@code paytech/}）
     * @return 全部违规
     */
    public static List<RuleViolation> checkReactorDependencyGraph(Path aggregatorRoot) {
        return checkDependencyGraph(new PomReader().readReactor(aggregatorRoot));
    }

    /**
     * 对给定模块集合执行依赖图规则。
     *
     * @param modules 模块
     * @return 全部违规
     */
    public static List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (DependencyGraphRule rule : dependencyGraphRules()) {
            violations.addAll(rule.checkDependencyGraph(modules));
        }
        return violations;
    }
}
