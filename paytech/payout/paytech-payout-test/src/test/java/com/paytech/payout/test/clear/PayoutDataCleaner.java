package com.paytech.payout.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.payout.test.dal.PayoutTestMapper;

/**
 * payout 本地 mor_payout 测试数据的基线检查与全局清理
 * <li>BeforeEach 先核对 3 张表齐全（缺表时提示按 init/ddl.sql 手工建表），再断言全部为空；发现遗留数据时失败而不删除。</li>
 * <li>AfterEach 按子表到父表删除全部测试写入；清理失败或异步工作未结束后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class PayoutDataCleaner implements Clear {
    /** 本服务的全部表，按删除顺序（子表在前）。 */
    public static final List<String> TABLES = List.of("t_beneficiary_account_event", "t_beneficiary_account",
        "t_compensation_event");

    private static boolean reusable = true;

    private final PayoutTestMapper mapper;

    /** 以测试 Mapper 构造。 */
    public PayoutDataCleaner(PayoutTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用、Schema 齐全且没有任何数据。 */
    public void assertEmpty() {
        assertReusable();
        assertEquals(new TreeSet<>(TABLES), new TreeSet<>(mapper.listTables()),
            "mor_payout 表清单与测试 DDL 不一致：请由管理员按 paytech-payout-test/src/test/resources/init/ddl.sql 手工建表"
                + "（测试不自动执行 DDL）");
        List<Map<String, Object>> counts = mapper.countWritableRows();
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 上一次清理失败或异步工作未结束时拒绝继续。 */
    public void assertReusable() {
        assertTrue(reusable, "上一次清理失败或异步工作未结束，测试环境不可复用；请手工核对 mor_payout 后重新运行");
    }

    /** 标记环境不可复用。 */
    public void markUnusable() {
        reusable = false;
    }

    /** 按依赖逆序删除测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            TABLES.forEach(mapper::deleteAll);
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }
}
