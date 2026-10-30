package com.paytech.tax.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.tax.test.dal.TaxTestMapper;

/**
 * tax 本地 mor_tax 测试数据的基线检查与全局清理（16 张表）
 * <li>BeforeEach 只检查空基线与初始化 DML 保留行，发现非测试遗留数据时失败而不删除。</li>
 * <li>AfterEach 按依赖逆序删除运行时表全部行与主数据表 1000 以上的行；清理失败后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class TaxDataCleaner implements Clear {
    /** 初始化 DML 保留行数：税类 1、地区包 3、注册 3、准入 3、号码序列 3。 */
    static final int RESERVED_ROWS = 13;

    private static final List<String> RUNTIME_TABLES = List.of("t_tax_document", "t_tax_reporting_entry",
        "t_tax_transaction", "t_tax_period", "t_tax_evidence", "t_tax_calculation_component", "t_tax_calculation_line",
        "t_tax_calculation", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version");

    private static final List<String> MASTER_TABLES = List.of("t_tax_document_sequence", "t_tax_market",
        "t_tax_registration", "t_tax_policy", "t_tax_category");

    private static boolean reusable = true;

    private final TaxTestMapper mapper;

    /** 注入测试 Mapper。 */
    public TaxDataCleaner(TaxTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用、初始化 DML 已执行且没有任何测试可写数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_tax 后重新运行");
        assertEquals(RESERVED_ROWS, mapper.countReservedRows(),
            "mor_tax 未执行 tax-initial-data.sql 或保留行被修改（见 paytech-tax-test init/ddl.sql）");
        List<Map<String, Object>> counts = mapper.countWritableRows();
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            RUNTIME_TABLES.forEach(mapper::deleteAll);
            MASTER_TABLES.forEach(mapper::deleteNonReserved);
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
