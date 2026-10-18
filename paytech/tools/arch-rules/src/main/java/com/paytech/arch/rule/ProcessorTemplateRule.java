package com.paytech.arch.rule;

import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.PackageMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R14：服务 {@code -core} 模块 {@code ..service.processor..} 包中类名以 {@code Processor} 结尾的具体类，必须直接或间接继承
 * Foundation {@code AbstractProcessor}（需要资源锁时经 {@code AbstractLockProcessor}），见 2026-09-16 决定第 8 条。
 *
 * <p>
 * 范围只含逻辑服务的 {@code -core} 模块（{@link ModuleIdentity#isServiceModule()} 且角色为 CORE）；抽象类、接口与不以
 * {@code Processor} 结尾的协作者（如 {@code *Preparer}）不检查，抽象中间类由其具体子类的可赋值性间接约束。
 *
 * <p>
 * 排除：
 * <li>BFF（{@value #CLIENT_BFF}、{@value #OPERATIONS_BFF}、{@value #PARTNER_BFF}）：其 Processor 是页面聚合层，不承担
 * 写命令的事务入口与资源锁职责，是否统一到模板由专门任务决定（2026-10-18 需求「Processor 模板迁移与 R14」）。</li>
 * <li>Foundation：模板本身所在，不属于逻辑服务，{@code isServiceModule()} 为 false。</li>
 *
 * @author Claude
 */
public final class ProcessorTemplateRule implements ArchitectureRule {

    /**
     * Foundation 同步命令模板。
     */
    public static final String DEFAULT_TEMPLATE = "com.paytech.foundation.processor.AbstractProcessor";

    /** Client Console BFF。 */
    static final String CLIENT_BFF = "client-console-bff";

    /** Operations Console BFF。 */
    static final String OPERATIONS_BFF = "operations-console-bff";

    /** Partner Console BFF。 */
    static final String PARTNER_BFF = "partner-console-bff";

    private static final Set<String> EXCLUDED_SERVICES = Set.of(CLIENT_BFF, OPERATIONS_BFF, PARTNER_BFF);

    private static final PackageMatcher PROCESSOR_PACKAGE = PackageMatcher.of("..service.processor..");

    private final String template;

    /**
     * 使用 Foundation {@code AbstractProcessor}。
     */
    public ProcessorTemplateRule() {
        this(DEFAULT_TEMPLATE);
    }

    /**
     * 使用指定的模板类全名（规则自测使用 fixture 模板）。
     *
     * @param template 模板类全名
     */
    public ProcessorTemplateRule(String template) {
        this.template = template;
    }

    @Override
    public String id() {
        return "R14";
    }

    @Override
    public String description() {
        return "服务 -core 的 service.processor 包中具体 *Processor 必须继承 Foundation AbstractProcessor / AbstractLockProcessor";
    }

    @Override
    public List<RuleViolation> check(ServiceArchitectureScope scope) {
        if (EXCLUDED_SERVICES.contains(scope.serviceName())) {
            return List.of();
        }
        List<RuleViolation> violations = new ArrayList<>();
        for (JavaClass javaClass : scope.classes()) {
            if (inScope(scope, javaClass) && !javaClass.isAssignableTo(template)) {
                violations.add(new RuleViolation(id(), javaClass.getName(),
                    "具体 Processor 未继承 " + template + "（需要资源锁时继承 AbstractLockProcessor）"));
            }
        }
        return violations;
    }

    /** 目标服务 -core 模块 service.processor 包中以 Processor 结尾的具体顶层或成员类。 */
    private boolean inScope(ServiceArchitectureScope scope, JavaClass javaClass) {
        Optional<ModuleIdentity> owner = scope.ownership().ownerOf(javaClass);
        boolean coreModule = owner.filter(identity -> identity.isServiceModule()
            && scope.serviceName().equals(identity.service()) && identity.role() == ModuleRole.CORE).isPresent();
        return coreModule && PROCESSOR_PACKAGE.matches(javaClass.getPackageName())
            && javaClass.getSimpleName().endsWith("Processor") && !javaClass.isInterface()
            && !javaClass.isAnonymousClass() && !javaClass.isLocalClass()
            && !javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
    }
}
