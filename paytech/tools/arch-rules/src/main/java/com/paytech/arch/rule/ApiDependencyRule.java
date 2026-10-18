package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * R2：{@code -api} 只能依赖 {@code paytech-common-core}、spring-web、validation（design/01 §2）。
 *
 * <p>
 * Lombok 以 {@code provided} 范围声明时不进入制品，视为允许（DTO 约定使用 Lombok 访问器）。
 *
 * @author Claude
 */
public final class ApiDependencyRule implements ArchitectureRule, DependencyGraphRule {

    private static final Set<String> ALLOWED = Set.of("com.paytech:paytech-common-core",
        "org.springframework:spring-web", "jakarta.validation:jakarta.validation-api");

    private static final String LOMBOK = "org.projectlombok:lombok";

    @Override
    public String id() {
        return "R2";
    }

    @Override
    public String description() {
        return "-api 只能依赖 paytech-common-core、spring-web、jakarta.validation-api";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (module.identity().role() != ModuleRole.API) {
                continue;
            }
            for (MavenDependency dependency : module.productionDependencies()) {
                if (isAllowed(dependency)) {
                    continue;
                }
                violations.add(new RuleViolation(id(), module.coordinate(),
                    "api 模块存在不允许的依赖: " + dependency.coordinate() + " (" + dependency.scope() + ")"));
            }
        }
        return violations;
    }

    private static boolean isAllowed(MavenDependency dependency) {
        if (ALLOWED.contains(dependency.coordinate())) {
            return true;
        }
        return LOMBOK.equals(dependency.coordinate()) && "provided".equals(dependency.scope());
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        String service = scope.serviceName();
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.API))
                .should().dependOnClassesThat(scope.ownedBy("api 不允许引用的 PayTech 模块中的类",
                    identity -> isForbiddenForApi(service, identity)))
                .because(description()),
            scope.classes()));
        return violations;
    }

    private static boolean isForbiddenForApi(String service, ModuleIdentity identity) {
        if (identity.role() == ModuleRole.UNCLASSIFIED || ALLOWED.contains(identity.coordinate())) {
            return false;
        }
        return !(service.equals(identity.service()) && identity.role() == ModuleRole.API);
    }
}
