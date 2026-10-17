package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ClassOwnership;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * R6：{@code -app} 中其他服务 {@code -api} 接口的实现 Bean 只能来自 {@code HttpServiceClientFactory}，禁止本地类实现他服务
 * api 接口；依赖图上 {@code -app} 不得依赖其他服务的 core / app（D09 修订）。
 *
 * @author Claude
 */
public final class RemoteApiBindingRule implements ArchitectureRule, DependencyGraphRule {

    @Override
    public String id() {
        return "R6";
    }

    @Override
    public String description() {
        return "-app 只经 HttpServiceClientFactory 绑定他服务 api，不得本地实现或依赖他服务 core/app";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            if (module.identity().role() != ModuleRole.APP) {
                continue;
            }
            for (MavenDependency dependency : module.productionDependencies()) {
                if (dependency.identity().isOtherServiceRole(module.identity().service(), ModuleRole.CORE,
                    ModuleRole.APP)) {
                    violations.add(new RuleViolation(id(), module.coordinate(),
                        "app 依赖了其他服务的 core/app 模块，进程内绑定只允许出现在 -test: "
                            + dependency.coordinate()));
                }
            }
        }
        return violations;
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>(checkDependencyGraph(scope.targetModules()));
        violations.addAll(ArchRuleEvaluation.violations(id(),
            classes().that(scope.ownedByTarget(ModuleRole.APP))
                .should(notImplementOtherServiceApi(scope.serviceName(), scope.ownership()))
                .because(description()),
            scope.classes()));
        return violations;
    }

    private static ArchCondition<JavaClass> notImplementOtherServiceApi(String service, ClassOwnership ownership) {
        return new ArchCondition<>("不实现其他服务 api 接口") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaClass contract : javaClass.getAllRawInterfaces()) {
                    boolean otherServiceApi = ownership.ownerOf(contract)
                        .filter(identity -> identity.isOtherServiceRole(service, ModuleRole.API, ModuleRole.CLIENT))
                        .isPresent();
                    if (otherServiceApi) {
                        events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                            + " 在本地实现了其他服务的 api 接口 " + contract.getName()));
                    }
                }
            }
        };
    }
}
