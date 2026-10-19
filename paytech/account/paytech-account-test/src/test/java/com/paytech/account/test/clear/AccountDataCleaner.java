package com.paytech.account.test.clear;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.annotation.Order;

import com.paytech.account.test.dal.AccountTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/**
 * Account 自有库 mor_account 测试数据的基线检查与全局清理
 * <li>清理范围等于测试 DDL 的 9 张表：六张明细账表、t_consumed_event、t_consumed_aggregate_version 与 t_compensation_event；没有保留数据。</li>
 * <li>BeforeEach 只检查空基线，发现非测试遗留数据时失败而不删除；AfterEach 按引用逆序删除，清理失败后环境标记为不可复用。</li>
 */
@TestComponent
@Order(1)
public class AccountDataCleaner implements Clear {
    private static final List<String> TABLES = List.of("t_account_entry", "t_balance_movement", "t_balance_applied_fact",
        "t_account_posting", "t_account_posting_stream", "t_owner_account", "t_consumed_event",
        "t_consumed_aggregate_version", "t_compensation_event");

    private static boolean reusable = true;

    private final AccountTestMapper mapper;

    public AccountDataCleaner(AccountTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 检查环境可复用且 mor_account 全部表为空。 */
    public void assertEmpty() {
        assertTrue(reusable, "上一次清理失败，测试环境不可复用；请手工核对 mor_account 后重新运行");
        List<Map<String, Object>> counts = mapper.countRows();
        assertEquals(TABLES.size(), counts.size(), "清理范围必须等于 mor_account 测试 DDL 的全部表");
        assertAll(counts.stream().map(row -> () -> assertEquals(0L, ((Number)row.get("c")).longValue(),
            "测试开始前 " + row.get("t") + " 应为空；若是本地开发数据请先自行处理，测试不会删除它")));
    }

    /** 按引用逆序删除全部测试写入；任何失败都标记环境不可复用并上抛。 */
    @Override
    public void clear() {
        try {
            TABLES.forEach(mapper::deleteAll);
        } catch (RuntimeException e) {
            reusable = false;
            throw e;
        }
    }

    /** 标记环境不可复用（异步写入未停止时）。 */
    public void markUnusable() {
        reusable = false;
    }

    /** 环境被标记不可复用时拒绝清理。 */
    public void assertReusable() {
        assertTrue(reusable, "测试环境不可复用：异步写入未停止或上一次清理失败");
    }
}
