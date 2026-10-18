package com.paytech.arch.auto;

import com.paytech.arch.ArchitectureRules;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;

import org.junit.jupiter.api.Test;

/**
 * R1、R2、R3、R6、R8、R13 的 Maven 依赖图部分：从 POM 树 fixture 读取模块与依赖，不调用 Maven。
 *
 * @see com.paytech.arch.ArchitectureRules#checkReactorDependencyGraph(java.nio.file.Path)
 * @author Claude
 */
class DependencyGraphRulesAutoTest {

    @Test
    void test_normalFlow() {
        for (String reactor : new String[] {"r1-pass", "r2-pass", "r3-pass", "r6-pass", "r8-pass", "r13-pass"}) {
            ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/" + reactor)))
                .none();
        }
    }

    @Test
    void test_coreDependsOnOtherServiceCore() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r1-violation")))
            .allFrom("R1")
            .count(2)
            .contains("paytech-ledger-core", "com.paytech:paytech-payment-core")
            .contains("paytech-ledger-core", "com.paytech:paytech-payment-app");
    }

    @Test
    void test_apiDependsOnForbiddenArtifact() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r2-violation")))
            .allFrom("R2")
            .count(3)
            .contains("paytech-ledger-api", "paytech-mvc-web-starter")
            .contains("paytech-ledger-api", "paytech-payment-api")
            .contains("paytech-ledger-api", "org.projectlombok:lombok (compile)");
    }

    @Test
    void test_domainCoreDependsOnProcessApi() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r3-violation")))
            .allFrom("R3")
            .count(2)
            .contains("paytech-ledger-core", "paytech-payment-api")
            .contains("paytech-ledger-core", "paytech-acquiring-api");
    }

    @Test
    void test_appDependsOnOtherServiceCore() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r6-violation")))
            .allFrom("R6")
            .count(1)
            .contains("paytech-ledger-app", "paytech-payment-core");
    }

    @Test
    void test_coreDependsOnMessagingImplementation() {
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r8-violation")))
            .allFrom("R8")
            .count(1)
            .contains("paytech-ledger-core", "paytech-messaging-api");
    }

    @Test
    void test_dependsOnNonReactorCoordinate() {
        // 旧工程坐标被拒绝；Foundation 的 test-jar 与非 com.paytech 依赖不受本规则约束。
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/r13-violation")))
            .allFrom("R13")
            .count(1)
            .contains("paytech-api-gateway-app", "com.paytech:platform-support-api");
    }

    @Test
    void test_edgeCase() {
        // 服务聚合 POM 的 <dependencies> 被子模块继承，同样受依赖图规则约束。
        ViolationChecker.of(ArchitectureRules.checkReactorDependencyGraph(Fixtures.path("graph/inherited")))
            .allFrom("R1")
            .count(1)
            .contains("paytech-ledger-core", "paytech-payment-core");
    }
}
