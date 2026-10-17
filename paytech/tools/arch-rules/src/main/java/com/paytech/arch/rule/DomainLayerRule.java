package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.catalog.ServiceCatalog;
import com.paytech.arch.catalog.ServiceLayer;
import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * R3：领域层服务的 {@code -core} 不得依赖流程层服务（acquiring、payment、payout、dispute、fx）的 {@code -api}（D08）。
 *
 * @author Claude
 */
public final class DomainLayerRule implements ArchitectureRule, DependencyGraphRule {

    @Override
    public String id() {
        return "R3";
    }

    @Override
    public String description() {
        return "领域层 -core 不得依赖流程层服务的 -api";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (!isDomainCore(module.identity())) {
                continue;
            }
            for (MavenDependency dependency : module.productionDependencies()) {
                if (isProcessApi(dependency.identity())) {
                    violations.add(new RuleViolation(id(), module.coordinate(),
                        "领域层 core 依赖了流程层 api: " + dependency.coordinate()));
                }
            }
        }
        return violations;
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        if (ServiceCatalog.layerOf(scope.serviceName()) != ServiceLayer.DOMAIN) {
            return violations;
        }
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.CORE))
                .should().dependOnClassesThat(scope.ownedBy("流程层服务 api 中的类", DomainLayerRule::isProcessApi))
                .because(description()),
            scope.classes()));
        return violations;
    }

    private static boolean isDomainCore(ModuleIdentity identity) {
        return identity.role() == ModuleRole.CORE && ServiceCatalog.layerOf(identity.service()) == ServiceLayer.DOMAIN;
    }

    private static boolean isProcessApi(ModuleIdentity identity) {
        return identity.isServiceModule() && identity.hasRole(ModuleRole.API, ModuleRole.CLIENT)
            && ServiceCatalog.layerOf(identity.service()) == ServiceLayer.PROCESS;
    }
}
