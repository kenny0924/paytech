package com.paytech.arch.auto;

import com.paytech.arch.rule.MapperTableOwnershipRule;
import com.paytech.arch.scope.ServiceArchitectureScope;
import com.paytech.arch.test.Fixtures;
import com.paytech.arch.test.ViolationChecker;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R4：core 的 Mapper XML 只能引用本服务数据库的表。
 *
 * @see com.paytech.arch.rule.MapperTableOwnershipRule#check(ServiceArchitectureScope)
 * @author Claude
 */
class MapperTableOwnershipRuleAutoTest {
    @Test
    void test_normalFlow() {
        Path service = Fixtures.path("r4/pass/ledger");

        ViolationChecker.of(new MapperTableOwnershipRule().check(ServiceArchitectureScope.of("ledger",
            List.of(service.resolve("paytech-ledger-core"), service.resolve("paytech-ledger-app"))))).none();
    }

    @Test
    void test_businessRuleViolation() {
        Path service = Fixtures.path("r4/violation/ledger");

        ViolationChecker.of(new MapperTableOwnershipRule().check(ServiceArchitectureScope.of("ledger",
                List.of(service.resolve("paytech-ledger-core"), service.resolve("paytech-ledger-app")))))
            .allFrom("R4")
            .count(2)
            .contains("JournalEntryMapper.xml:6", "t_payment_intent")
            .contains("JournalEntryMapper.xml:7", "t_customer");
    }

    @Test
    void test_edgeCase() {
        // R4 只约束 core；app 模块的 Mapper XML 不在范围内。
        Path service = Fixtures.path("r4/violation/ledger");

        ViolationChecker.of(new MapperTableOwnershipRule().check(ServiceArchitectureScope.of("ledger",
            List.of(service.resolve("paytech-ledger-app"))))).none();
    }

    @Test
    void test_noDataFound() {
        // 没有数据库的服务（网关）不允许引用任何表。
        Path core = Fixtures.path("r4/no-database/api-gateway/paytech-api-gateway-core");

        ViolationChecker.of(new MapperTableOwnershipRule().check(ServiceArchitectureScope.of("api-gateway",
                List.of(core))))
            .count(1)
            .contains("RouteMapper.xml:1", "t_gateway_route");
    }
}
