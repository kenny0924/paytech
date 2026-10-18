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
 * R1：{@code -core} 不得依赖任何其他服务的 {@code -core} 或 {@code -app}（Maven 依赖图与 import 双重检查，D09）。
 *
 * @author Claude
 */
public final class CoreIsolationRule implements ArchitectureRule, DependencyGraphRule {

    @Override
    public String id() {
        return "R1";
    }

    @Override
    public String description() {
        return "-core 不得依赖其他服务的 -core 或 -app";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (module.identity().role() != ModuleRole.CORE) {
                continue;
            }
            for (MavenDependency dependency : module.productionDependencies()) {
                if (dependency.identity().isOtherServiceRole(module.identity().service(), ModuleRole.CORE,
                    ModuleRole.APP)) {
                    violations.add(new RuleViolation(id(), module.coordinate(),
                        "依赖了其他服务的 core/app 模块: " + dependency.coordinate()));
                }
            }
        }
        return violations;
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.CORE))
                .should().dependOnClassesThat(scope.ownedByOtherService(ModuleRole.CORE, ModuleRole.APP))
                .because(description()),
            scope.classes()));
        return violations;
    }
}
