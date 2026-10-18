package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import java.util.ArrayList;
import java.util.List;

/**
 * R9：DataObject（{@code core/dal/dataobject}）不得出现在 {@code -api} 中，也不得被其他服务引用（现行 DataObject 边界）。
 *
 * @author Claude
 */
public final class DataObjectBoundaryRule implements ArchitectureRule {

    private static final String DATA_OBJECT_PACKAGE = "..dal.dataobject..";

    @Override
    public String id() {
        return "R9";
    }

    @Override
    public String description() {
        return "DataObject 不得出现在 -api，也不得被其他服务引用";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        JavaClasses classes = scope.classes();
        DescribedPredicate<JavaClass> dataObject = DescribedPredicate.describe("DataObject",
            javaClass -> javaClass.getBaseComponentType().getPackageName().contains(".dal.dataobject"));
        DescribedPredicate<JavaClass> otherServiceDataObject = DescribedPredicate.describe("其他服务的 DataObject",
            javaClass -> dataObject.test(javaClass) && scope.ownership().ownerOf(javaClass)
                .filter(identity -> identity.isServiceModule() && !scope.serviceName().equals(identity.service()))
                .isPresent());
        List<RuleViolation> violations = new ArrayList<>();
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.API)).should().resideInAPackage(DATA_OBJECT_PACKAGE)
                .because(description()),
            classes));
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.API)).should().dependOnClassesThat(dataObject)
                .because(description()),
            classes));
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noClasses().that(scope.ownedByTarget(ModuleRole.values())).should()
                .dependOnClassesThat(otherServiceDataObject)
                .because(description()),
            classes));
        return violations;
    }
}
