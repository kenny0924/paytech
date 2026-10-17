package com.paytech.arch.rule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ClassOwnership;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.support.ArchRuleEvaluation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * R7：事务边界内禁止调用外部客户端。标注 {@code @Transactional} 或使用 {@code TransactionTemplate} 的类不得直接注入
 * {@code -api} HTTP 接口、{@code VaultClient}、{@code RabbitTemplate}（事务公理）。
 *
 * <p>
 * "直接注入"指字段类型与构造器参数类型。HTTP 接口按两种方式识别：类型归属于某服务的 api / client 模块，或类型（及其方法）
 * 带 {@code @HttpExchange} 元注解。
 *
 * @author Claude
 */
public final class TransactionExternalCallRule implements ArchitectureRule {

    private static final List<String> TRANSACTIONAL = List.of(
        "org.springframework.transaction.annotation.Transactional", "jakarta.transaction.Transactional");

    private static final String TRANSACTION_OPERATIONS = "org.springframework.transaction.support.TransactionOperations";

    private static final String HTTP_EXCHANGE = "org.springframework.web.service.annotation.HttpExchange";

    private static final List<String> MESSAGE_TEMPLATES = List.of("org.springframework.amqp.core.AmqpTemplate",
        "org.springframework.amqp.rabbit.core.RabbitOperations",
        "org.springframework.amqp.rabbit.core.RabbitTemplate");

    @Override
    public String id() {
        return "R7";
    }

    @Override
    public String description() {
        return "事务类不得直接注入 -api HTTP 接口、VaultClient、RabbitTemplate";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        return ArchRuleEvaluation.violations(id(),
            classes().that(scope.ownedByTarget(ModuleRole.CORE))
                .should(notInjectExternalClientsWhenTransactional(scope.ownership()))
                .because(description()),
            scope.classes());
    }

    private ArchCondition<JavaClass> notInjectExternalClientsWhenTransactional(ClassOwnership ownership) {
        return new ArchCondition<>("事务类不直接注入外部客户端") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!isTransactional(javaClass)) {
                    return;
                }
                for (JavaClass injected : injectedTypes(javaClass)) {
                    String reason = externalClientReason(injected, ownership);
                    if (reason != null) {
                        events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                            + " 处于事务边界，却直接注入了" + reason + " " + injected.getName()));
                    }
                }
            }
        };
    }

    private static boolean isTransactional(JavaClass javaClass) {
        for (String annotation : TRANSACTIONAL) {
            if (javaClass.isAnnotatedWith(annotation) || javaClass.isMetaAnnotatedWith(annotation)) {
                return true;
            }
            boolean annotatedMethod = javaClass.getMethods().stream()
                .anyMatch(method -> method.isAnnotatedWith(annotation) || method.isMetaAnnotatedWith(annotation));
            if (annotatedMethod) {
                return true;
            }
        }
        boolean transactionField = javaClass.getFields().stream()
            .anyMatch(field -> field.getRawType().isAssignableTo(TRANSACTION_OPERATIONS));
        if (transactionField) {
            return true;
        }
        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
            if (call.getTargetOwner().isAssignableTo(TRANSACTION_OPERATIONS)) {
                return true;
            }
        }
        return false;
    }

    private static Set<JavaClass> injectedTypes(JavaClass javaClass) {
        Set<JavaClass> types = new LinkedHashSet<>();
        for (JavaField field : javaClass.getFields()) {
            if (!field.getModifiers().contains(JavaModifier.STATIC)) {
                types.add(field.getRawType());
            }
        }
        for (JavaConstructor constructor : javaClass.getConstructors()) {
            types.addAll(constructor.getRawParameterTypes());
        }
        return types;
    }

    private static String externalClientReason(JavaClass type, ClassOwnership ownership) {
        if (type.getSimpleName().equals("VaultClient") || ownership.ownerOf(type)
            .filter(identity -> "vault".equals(identity.service()) && identity.role() == ModuleRole.CLIENT)
            .isPresent()) {
            return " Vault 客户端";
        }
        boolean ownedByApi = ownership.ownerOf(type)
            .filter(identity -> identity.hasRole(ModuleRole.API, ModuleRole.CLIENT))
            .isPresent();
        if (type.isInterface() && (ownedByApi || isHttpExchange(type))) {
            return " HTTP 接口";
        }
        for (String template : MESSAGE_TEMPLATES) {
            if (type.getName().equals(template) || type.isAssignableTo(template)) {
                return " MQ 发送模板";
            }
        }
        return null;
    }

    private static boolean isHttpExchange(JavaClass type) {
        if (type.isAnnotatedWith(HTTP_EXCHANGE) || type.isMetaAnnotatedWith(HTTP_EXCHANGE)) {
            return true;
        }
        return type.getMethods().stream()
            .anyMatch(method -> method.isAnnotatedWith(HTTP_EXCHANGE) || method.isMetaAnnotatedWith(HTTP_EXCHANGE));
    }

}
