package com.paytech.arch.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.rule.ArchitectureRule;
import com.paytech.arch.rule.RuleViolation;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 无 core 服务（只有 {@code -app} 与 {@code -test}，如 api-gateway、两个 BFF）执行 R1–R12。
 * <li>R1、R3、R4、R5 只约束 core，没有 core 时无检查对象而通过；启动类与 Controller 留在 app 合法。</li>
 * <li>app 依赖其他服务 core / app 仍由 R6 依赖图检查拦截，无 core 不等于无隔离。</li>
 *
 * @see com.paytech.arch.ArchitectureRules#checkService(String, java.util.Collection)
 * @author Claude
 */
class NoCoreServiceAutoTest {

    private static final String SERVICE = "api-gateway";

    @Test
    void test_normalFlow() {
        ServiceArchitectureScope scope = Fixtures.scope(SERVICE)
            .module(Fixtures.module(SERVICE, "app"), Fixtures.classes("nocore.gatewayapp"))
            .module(Fixtures.module(SERVICE, "test"), Fixtures.classes("nocore.gatewaytest"))
            .build();

        ViolationChecker.of(checkAll(scope)).none();
    }

    @Test
    void test_businessRuleViolation() {
        Path app = Fixtures.path("nocore/violation/api-gateway/paytech-api-gateway-app");
        ServiceArchitectureScope scope = Fixtures.scope(SERVICE)
            .module(app, Fixtures.classes("nocore.gatewayapp"))
            .module(Fixtures.module(SERVICE, "test"), Fixtures.classes("nocore.gatewaytest"))
            .build();

        ViolationChecker.of(checkAll(scope))
            .allFrom("R6")
            .count(1)
            .contains("paytech-api-gateway-app", "paytech-payment-core");
    }

    /**
     * 对检查对象依次执行全部服务级规则。
     *
     * @param scope 检查对象
     * @return 全部违规
     */
    private static List<RuleViolation> checkAll(ServiceArchitectureScope scope) {
        List<RuleViolation> violations = new ArrayList<>();
        for (ArchitectureRule rule : ArchitectureRules.all()) {
            violations.addAll(rule.check(scope));
        }
        return violations;
    }
}
