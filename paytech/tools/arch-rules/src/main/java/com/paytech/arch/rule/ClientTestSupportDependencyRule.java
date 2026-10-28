package com.paytech.arch.rule;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleIdentity;
import com.paytech.arch.model.ModuleRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * R16：服务 Java Client 的测试支持模块（{@code -client-test-support}）只允许其他服务的 {@code -test} 模块依赖。
 * <li>测试支持包含测试 HTTP 端点、Mocker 与 Builder，进入生产制品即违反测试隔离；因此任何范围（含 test）的依赖都检查。</li>
 * <li>提供方本服务的模块也不得依赖：提供方客户端的自身测试放在测试支持模块的 {@code src/test} 中。</li>
 *
 * @author Claude
 */
public final class ClientTestSupportDependencyRule implements DependencyGraphRule {

    @Override
    public String id() {
        return "R16";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            for (MavenDependency dependency : module.dependencies()) {
                ModuleIdentity target = dependency.identity();
                if (target.role() != ModuleRole.CLIENT_TEST_SUPPORT || isOtherServiceTest(module.identity(), target)) {
                    continue;
                }
                violations.add(new RuleViolation(id(), module.coordinate(), "客户端测试支持只允许其他服务的 -test 模块依赖: "
                    + dependency.coordinate() + " (" + dependency.scope() + ")"));
            }
        }
        return violations;
    }

    /** 依赖方是否为测试支持所属服务以外的服务 {@code -test} 模块。 */
    private static boolean isOtherServiceTest(ModuleIdentity module, ModuleIdentity target) {
        return module.isOtherServiceRole(target.service(), ModuleRole.TEST);
    }
}
