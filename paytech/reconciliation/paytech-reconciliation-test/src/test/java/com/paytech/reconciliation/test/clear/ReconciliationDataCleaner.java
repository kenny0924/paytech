package com.paytech.reconciliation.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.reconciliation.test.dal.ReconciliationTestMapper;

/**
 * Reconciliation 本地 mor_reconciliation 测试数据的基线检查与全局清理（design/10 §3.1）
 * <li>本期只有 Schema 归属、没有测试写入：BeforeEach 断言全部 18 张表为空，AfterEach 仍按表清单删除以兜底。</li>
 * <li>发现非测试遗留数据时失败而不删除；清理失败后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class ReconciliationDataCleaner implements Clear {
    private static final List<String> FULLY_OWNED_TABLES = List.of("t_compensation_event", "t_recon_resolution", "t_recon_exception", "t_bank_recon_match",
        "t_bank_statement_item", "t_bank_statement", "t_cash_account_binding_ver",
        "t_cash_account_binding", "t_cash_account", "t_channel_transaction_match", "t_channel_settle_item",
        "t_channel_settle_batch", "t_duplicate_capture_item", "t_channel_debit_item",
        "t_channel_open_item", "t_channel_account", "t_channel_cost_alloc", "t_channel_cost_confirm");

    private static boolean reusable = true;

    private final ReconciliationTestMapper mapper;

    public ReconciliationDataCleaner(ReconciliationTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用且没有任何测试可写数据。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_reconciliation 后重新运行");
        List<Map<String, Object>> counts = mapper.countWritableRows();
        assertEquals(18, counts.size(), "mor_reconciliation 表清单与测试 DDL 不一致");
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            FULLY_OWNED_TABLES.forEach(mapper::deleteAll);
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
