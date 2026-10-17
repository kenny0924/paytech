package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.tngtech.archunit.core.domain.JavaClasses;

import java.util.ArrayList;
import java.util.List;

/**
 * R5：{@code -core} 不含启动类与 Controller；{@code @RabbitListener} 等 MQ 消费注解允许留在 core（design/01 §2）。
 *
 * @author Claude
 */
public final class CoreWebBootstrapRule implements ArchitectureRule {

    private static final List<String> FORBIDDEN_TYPE_ANNOTATIONS = List.of(
        "org.springframework.boot.autoconfigure.SpringBootApplication",
        "org.springframework.boot.SpringBootConfiguration",
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration",
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.ControllerAdvice",
        "org.springframework.web.bind.annotation.RequestMapping");

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    @Override
    public String id() {
        return "R5";
    }

    @Override
    public String description() {
        return "-core 不得声明启动注解、Controller 或请求映射，Controller 只在 -app";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        JavaClasses classes = scope.classes();
        List<RuleViolation> violations = new ArrayList<>();
        for (String annotation : FORBIDDEN_TYPE_ANNOTATIONS) {
            violations.addAll(ArchRuleEvaluation.violations(id(),
                noClasses().that(scope.ownedByTarget(ModuleRole.CORE))
                    .should().beAnnotatedWith(annotation)
                    .orShould().beMetaAnnotatedWith(annotation)
                    .because(description()),
                classes));
        }
        violations.addAll(ArchRuleEvaluation.violations(id(),
            noMethods().that().areDeclaredInClassesThat(scope.ownedByTarget(ModuleRole.CORE))
                .should().beAnnotatedWith(REQUEST_MAPPING)
                .orShould().beMetaAnnotatedWith(REQUEST_MAPPING)
                .because(description()),
            classes));
        return violations;
    }
}
