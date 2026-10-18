package com.paytech.billing.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.billing.test.dal.BillingTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * Billing 本地 mor_billing 测试数据的基线检查与全局清理（由 FinanceDataCleaner 摘出，design/10 §3.1）
 * <li>BeforeEach 只检查空基线，发现非测试遗留数据时失败而不删除，避免误清本地开发数据。</li>
 * <li>AfterEach 按依赖逆序删除全部测试写入；t_currency_spec 为只读副本保留表，只删除非保留 ID。清理失败后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class BillingDataCleaner implements Clear {
    /** 初始化 DML 保留行数：USD 1。 */
    static final int RESERVED_ROWS = 1;

    private static final List<String> FULLY_OWNED_TABLES = List.of("t_channel_cost_component", "t_channel_cost_scope",
        "t_channel_cost_plan_ver", "t_channel_cost_plan", "t_economic_fact_line_link", "t_economic_fact_line",
        "t_economic_fact", "t_compensation_event");

    private static final List<String> RESERVED_TABLES = List.of("t_currency_spec");

    private static boolean reusable = true;

    private final BillingTestMapper mapper;

    public BillingDataCleaner(BillingTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用、初始化 DML 已执行且没有任何测试可写数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_billing 后重新运行");
        assertEquals(RESERVED_ROWS, mapper.countReservedRows(),
            "mor_billing 未执行 billing-initial-data.sql 或保留行被修改");
        List<Map<String, Object>> counts = mapper.countWritableRows();
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            FULLY_OWNED_TABLES.forEach(mapper::deleteAll);
            RESERVED_TABLES.forEach(mapper::deleteNonReserved);
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
