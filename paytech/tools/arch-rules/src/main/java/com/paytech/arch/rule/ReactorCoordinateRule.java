package com.paytech.arch.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.paytech.arch.model.MavenDependency;
import com.paytech.arch.model.MavenModule;
import com.paytech.arch.model.ModuleRole;

/**
 * R13：Reactor 内任何模块声明的 {@code com.paytech} 依赖（含 test 范围）都必须是本 Reactor 命名体系内的制品。
 * <li>搬迁后旧工程坐标（如 {@code platform-support-api}、{@code finance-service-api}）仍能从本地仓库解析到过期 JAR，编译与测试都看不出偏差，
 * 只有运行期才暴露；本规则在 POM 层面直接拒绝。</li>
 * <li>判定依据是 {@link com.paytech.arch.model.ModuleIdentity#classify}：能归入 Build / Foundation / Tool / 服务目录的坐标放行（它们是否真在
 * Reactor 由 Maven 解析本身保证），归不进任何角色的 {@code com.paytech} 坐标即为旧工程制品。角色约束由 R1、R2、R3、R6、R8 负责。</li>
 *
 * @author Claude
 */
public final class ReactorCoordinateRule implements DependencyGraphRule {

    /** 本仓库制品的 groupId。 */
    private static final String PAYTECH_GROUP = "com.paytech";

    @Override
    public String id() {
        return "R13";
    }

    @Override
    public List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules) {
        List<RuleViolation> violations = new ArrayList<>();
        for (MavenModule module : modules) {
            for (MavenDependency dependency : module.dependencies()) {
                if (!PAYTECH_GROUP.equals(dependency.groupId())) {
                    continue;
                }
                if (dependency.identity().role() != ModuleRole.UNCLASSIFIED) {
                    continue;
                }
                violations.add(new RuleViolation(id(), module.coordinate(),
                    "依赖了 Reactor 命名体系之外的 com.paytech 坐标 " + dependency.coordinate()
                        + "（" + dependency.scope() + "），它只能从本地仓库解析到旧工程制品；改为对应的 Reactor 模块"));
            }
        }
        return violations;
    }
}
