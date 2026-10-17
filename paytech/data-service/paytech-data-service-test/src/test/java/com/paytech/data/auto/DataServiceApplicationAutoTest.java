package com.paytech.data.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.data.DataServiceApplication;
import com.paytech.data.app.health.DorisHealthIndicator;
import com.paytech.data.domain.whitelist.WhitelistedTable;
import com.paytech.data.domainservice.WhitelistCatalog;
import com.paytech.data.test.base.MockTestBase;
import com.paytech.data.test.checker.HealthResponseChecker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.List;

import javax.sql.DataSource;

/**
 * 正式应用以 {@code mock} Profile 启动：健康 UP、Doris 关闭时不创建 DataSource 与健康指示器、白名单目录含演示表。
 *
 * @see DataServiceApplication#main(String[])
 * @see WhitelistCatalog#tables()
 * @see WhitelistCatalog#find(String, String)
 * @author Claude
 */
class DataServiceApplicationAutoTest extends MockTestBase {

    private static final String DEMO_DATABASE = "mor_finance";

    private static final String DEMO_TABLE = "t_currency_spec";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WhitelistCatalog whitelistCatalog;

    @Test
    void test_healthUpWithoutDoris() {
        HealthResponseChecker.of(managementClient().get().uri("/actuator/health").exchange()
                .expectBody().returnResult())
            .status("UP")
            .component("ping", "UP")
            .noComponent("doris");
        assertEquals(0, context.getBeanNamesForType(DataSource.class).length, "Doris 关闭时不应有 DataSource Bean");
        assertEquals(0, context.getBeanNamesForType(DorisHealthIndicator.class).length, "Doris 关闭时不应注册健康指示器");
    }

    @Test
    void test_whitelistContainsDemoTable() {
        List<WhitelistedTable> tables = whitelistCatalog.tables();
        assertEquals(1, tables.size(), "阶段 0 白名单只登记演示表: " + tables);
        WhitelistedTable demo = whitelistCatalog.find(DEMO_DATABASE, DEMO_TABLE)
            .orElseThrow(() -> new AssertionError("白名单缺少演示表 " + DEMO_DATABASE + "." + DEMO_TABLE));
        assertEquals("ledger", demo.service());
        assertEquals(DEMO_DATABASE + "." + DEMO_TABLE, demo.qualifiedName());
        assertTrue(demo.maskedColumns().isEmpty(), "演示表没有 PII 列");
        assertTrue(demo.excludedColumns().isEmpty(), "演示表不排除列");
        assertTrue(whitelistCatalog.find(DEMO_DATABASE, "t_not_whitelisted").isEmpty(), "未登记的表不得出现在目录中");
    }
}
