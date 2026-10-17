package com.paytech.tax.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * Tax 本地 mor_tax 测试数据的基线检查与全局清理（design/10 §3.1）
 * <li>tax-app 不装配 MyBatis，清理经 JdbcTemplate 执行。本期只有 Schema 归属、没有测试写入：BeforeEach 断言全部 24 张表为空，AfterEach 仍按表清单删除以兜底。</li>
 * <li>发现非测试遗留数据时失败而不删除；清理失败后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class TaxDataCleaner implements Clear {
    private static final List<String> FULLY_OWNED_TABLES = List.of("t_compensation_event", "t_tax_payment_event", "t_tax_payment", "t_tax_return_line",
        "t_tax_return", "t_tax_liability_summary", "t_tax_period", "t_fiscal_record_line",
        "t_fiscal_record", "t_fiscal_number_sequence", "t_tax_line", "t_tax_evidence", "t_tax_snapshot",
        "t_tax_exchange_rate", "t_tax_evidence_rule", "t_tax_rate", "t_place_of_supply_rule", "t_tax_rule",
        "t_tax_treatment", "t_tax_category_mapping", "t_tax_category", "t_tax_registration",
        "t_tax_authority", "t_tax_jurisdiction");

    private static boolean reusable = true;

    private final JdbcTemplate jdbc;

    public TaxDataCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 检查环境可复用且没有任何测试可写数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_tax 后重新运行");
        assertEquals(24, FULLY_OWNED_TABLES.size(), "mor_tax 表清单与测试 DDL 不一致");
        assertAll(FULLY_OWNED_TABLES.stream().map(table -> () -> assertEquals(0L,
            jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class),
            "测试开始前 " + table + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            FULLY_OWNED_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
