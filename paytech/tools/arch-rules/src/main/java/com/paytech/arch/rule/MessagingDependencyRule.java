package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * R8：只有 {@code -app} 可以依赖 {@code paytech-messaging} 的发布端实现；{@code -core} 只依赖
 * {@code paytech-messaging-api}（信封与发布端口接口，design/08）。
 *
 * @author Claude
 */
public final class MessagingDependencyRule implements ArchitectureRule, DependencyGraphRule {

    /**
     * 发布端实现制品。
     */
    public static final String MESSAGING_IMPLEMENTATION = "com.paytech:paytech-messaging";

    @Override
    public String id() {
        return "R8";
    }

    @Override
    public String description() {
        return "只有 -app 可以依赖 paytech-messaging，-core 只依赖 paytech-messaging-api";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (!module.identity().isServiceModule() || module.identity().role() == ModuleRole.APP) {
                continue;
            }
            for (MavenDependency dependency : module.productionDependencies()) {
                if (MESSAGING_IMPLEMENTATION.equals(dependency.coordinate())) {
                    violations.add(new RuleViolation(id(), module.coordinate(),
                        module.identity().role() + " 模块依赖了消息发布端实现 " + dependency.coordinate()
                            + "，应改为 paytech-messaging-api"));
                }
            }
        }
        return violations;
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.CORE, ModuleRole.API))
                .should().dependOnClassesThat(scope.ownedBy("paytech-messaging 发布端实现中的类",
                    identity -> MESSAGING_IMPLEMENTATION.equals(identity.coordinate())))
                .because(description()),
            scope.classes()));
        return violations;
    }
}
