package com.paytech.platform.support.dataquery.auto.engine;

import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.COMMERCE_SOURCE;
import static com.paytech.platform.support.dataquery.test.builder.QueryTemplateFixtures.WHOLE_INSTANCE_SOURCE;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.engine.PreparedQuery;
import com.paytech.platform.support.dataquery.engine.QueryPaging;
import com.paytech.platform.support.dataquery.engine.QuerySource;
import com.paytech.platform.support.dataquery.engine.ReadOnlySqlGuard;
import com.paytech.platform.support.dataquery.engine.SqlInspection;
import com.paytech.platform.support.dataquery.engine.SqlPagingRewriter;
import com.paytech.platform.support.dataquery.template.RenderedQueryViolation;

import net.sf.jsqlparser.statement.select.SetOperationList;

/**
 * 渲染后 SQL 的只读校验（需求 AC-06、技术方案第 4.3 节与第 5.1 节）与分页改写（第 4.4 节）。
 *
 * @see ReadOnlySqlGuard#inspect(String, QuerySource)
 * @see ReadOnlySqlGuard#requireReadOnly(String, QuerySource)
 * @see SqlPagingRewriter#rewrite(net.sf.jsqlparser.statement.select.Select, List, QueryPaging)
 */
class ReadOnlySqlGuardTest {

    private static final String INTO_MESSAGE = "不允许 INTO（含 INTO OUTFILE、INTO DUMPFILE、INTO 变量）";

    private static final String LOCK_MESSAGE = "不允许 FOR UPDATE / FOR SHARE";

    private static final String SELECT_ONLY_MESSAGE = "只允许 SELECT 语句";

    private static final String COMMENT_MESSAGE = "不能包含 SQL 注释（/* */、--、#），模板注释请用 {{!-- --}}";

    private static final QuerySource DORIS_SOURCE = new QuerySource(DataSourceEngineType.DORIS, "mor_data_service");

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    private final SqlPagingRewriter rewriter = new SqlPagingRewriter();

    /** 写语句与 DDL 一律拒绝。 */
    @Test
    void test_rejectsWriteAndDdlStatements() {
        assertRejected("INSERT INTO t_order (id) VALUES (?)", INTO_MESSAGE);
        assertRejected("REPLACE INTO t_order (id) VALUES (1)", INTO_MESSAGE);
        assertRejected("UPDATE t_order SET status = ?", SELECT_ONLY_MESSAGE);
        assertRejected("DELETE FROM t_order WHERE id = ?", SELECT_ONLY_MESSAGE);
        assertRejected("CREATE TABLE t_copy (id BIGINT)", SELECT_ONLY_MESSAGE);
        assertRejected("DROP TABLE t_order", SELECT_ONLY_MESSAGE);
        assertRejected("ALTER TABLE t_order ADD COLUMN c INT", SELECT_ONLY_MESSAGE);
        assertRejected("TRUNCATE TABLE t_order", SELECT_ONLY_MESSAGE);
        assertRejected("(SELECT id FROM t_order)", "只允许 SELECT、UNION 或 WITH … SELECT");
    }

    /** 加锁读与 INTO（文件、变量）在任何一层都拒绝。 */
    @Test
    void test_rejectsLockingAndInto() {
        assertRejected("SELECT id FROM t_order FOR UPDATE", LOCK_MESSAGE);
        assertRejected("SELECT id FROM t_order FOR SHARE", LOCK_MESSAGE);
        assertRejected("SELECT id FROM t_order LOCK IN SHARE MODE", "不允许 LOCK IN SHARE MODE");
        assertRejected("SELECT id FROM t_order INTO OUTFILE '/tmp/orders.csv'", INTO_MESSAGE);
        assertRejected("SELECT id INTO DUMPFILE '/tmp/orders.bin' FROM t_order", INTO_MESSAGE);
        assertRejected("SELECT id INTO @last_id FROM t_order", INTO_MESSAGE);
        Assertions.assertFalse(guard.inspect("SELECT x.id FROM (SELECT id FROM t_order FOR UPDATE) x",
            COMMERCE_SOURCE).passed());
    }

    /** 多语句拒绝。 */
    @Test
    void test_rejectsMultipleStatements() {
        assertRejected("SELECT id FROM t_order; DELETE FROM t_order", "只允许一条语句");
        assertRejected("SELECT 1; SELECT 2", "只允许一条语句");
    }

    /** 字符串字面量外的注释（含 MySQL 可执行注释 /*! *&#47;）拒绝并定位；字面量内的同样字符允许。 */
    @Test
    void test_rejectsCommentsOutsideLiterals() {
        SqlInspection executable = guard.inspect(
            "SELECT id FROM t_order /*! UNION SELECT authentication_string FROM mysql.user */", COMMERCE_SOURCE);

        assertRejected("SELECT id FROM t_order -- trailing", COMMENT_MESSAGE);
        assertRejected("SELECT id FROM t_order # trailing", COMMENT_MESSAGE);
        Assertions.assertEquals(List.of(COMMENT_MESSAGE), messages(executable));
        Assertions.assertEquals(23, executable.getViolations().getFirst().getOffset());
        assertPassed("SELECT id FROM t_order WHERE note = '-- not # a /* comment */' AND tag = \"it''s\"");
    }

    /** 黑名单函数在任何位置都拒绝，不区分大小写。 */
    @Test
    void test_rejectsBlockedFunctions() {
        assertRejected("SELECT SLEEP(1) FROM t_order", "不允许调用函数 SLEEP");
        assertRejected("SELECT id FROM t_order WHERE id = (SELECT MAX(id) FROM t_order WHERE BENCHMARK(10, MD5('a')) = 0)",
            "不允许调用函数 BENCHMARK");
        assertRejected("SELECT get_lock('job', 1) FROM t_order", "不允许调用函数 GET_LOCK");
        assertRejected("SELECT LOAD_FILE('/etc/passwd') FROM t_order", "不允许调用函数 LOAD_FILE");
    }

    /** 系统库按方言拒绝，information_schema 允许；默认库本身是系统库时未限定的表同样拒绝。 */
    @Test
    void test_rejectsSystemSchemas() {
        assertRejected("SELECT user, authentication_string FROM mysql.user", "不允许引用系统库 mysql 中的表 user");
        assertRejected("SELECT * FROM `sys`.`processlist`", "不允许引用系统库 sys 中的表 processlist");
        assertRejected("SELECT * FROM PERFORMANCE_SCHEMA.threads", "不允许引用系统库 performance_schema 中的表 threads");
        assertPassed("SELECT table_name FROM information_schema.tables WHERE table_schema = ?");
        Assertions.assertEquals(List.of("不允许引用系统库 __internal_schema 中的表 audit_log"),
            messages(guard.inspect("SELECT * FROM __internal_schema.audit_log", DORIS_SOURCE)));
        Assertions.assertTrue(guard.inspect("SELECT * FROM sys.x", DORIS_SOURCE).passed());
        Assertions.assertEquals(List.of("不允许引用系统库 mysql 中的表 user"), messages(guard.inspect("SELECT * FROM user",
            new QuerySource(DataSourceEngineType.MYSQL, "mysql"))));
    }

    /** 整实例数据源：未限定库名的表拒绝并定位；CTE 与 dual 不算表。 */
    @Test
    void test_wholeInstanceRequiresQualifiedTables() {
        String sql = "SELECT o.id FROM mor_commerce.t_order o JOIN t_refund r ON r.order_id = o.id";

        SqlInspection inspection = guard.inspect(sql, WHOLE_INSTANCE_SOURCE);

        Assertions.assertEquals(List.of("表 t_refund 未限定库名：数据源未指定默认库，请写成 库.表"), messages(inspection));
        Assertions.assertEquals(sql.indexOf("t_refund"), inspection.getViolations().getFirst().getOffset());
        Assertions.assertTrue(guard.inspect("WITH recent AS (SELECT id FROM mor_commerce.t_order) SELECT id FROM recent",
            WHOLE_INSTANCE_SOURCE).passed());
        Assertions.assertTrue(guard.inspect("SELECT 1 FROM dual", WHOLE_INSTANCE_SOURCE).passed());
        assertPassed(sql);
    }

    /** 顶层不能带 LIMIT / OFFSET；子查询内的 LIMIT 允许。 */
    @Test
    void test_rejectsTopLevelLimit() {
        assertRejected("SELECT id FROM t_order LIMIT 10", "正文不能带顶层 LIMIT / OFFSET，分页由服务追加");
        assertRejected("SELECT id FROM t_order LIMIT 10 OFFSET 5", "正文不能带顶层 LIMIT / OFFSET，分页由服务追加");
        assertPassed("SELECT x.id FROM (SELECT id FROM t_order ORDER BY id LIMIT 5) x");
    }

    /** 解析器无法解析的语句一律拒绝。 */
    @Test
    void test_unparsableSqlRejected() {
        assertRejected("SELECT id FROM t_order WHERE", "解析器不支持该语法");
    }

    /** UNION 与 WITH 正例。 */
    @Test
    void test_acceptsUnionAndWith() {
        SqlInspection union = guard.inspect("SELECT id FROM t_order UNION ALL SELECT order_id FROM t_refund",
            COMMERCE_SOURCE);

        Assertions.assertTrue(union.passed(), () -> messages(union).toString());
        Assertions.assertInstanceOf(SetOperationList.class, union.getSelect());
        assertPassed("WITH paid AS (SELECT id, client_id FROM t_order WHERE status = ?) "
            + "SELECT client_id, COUNT(*) FROM paid GROUP BY client_id HAVING COUNT(*) > ?");
    }

    /** 执行路径要求通过，否则抛出业务检查异常并列出原因。 */
    @Test
    void test_requireReadOnlyThrows() {
        BizCheckFailException exception = Assertions.assertThrows(BizCheckFailException.class,
            () -> guard.requireReadOnly("SELECT SLEEP(1) FROM mysql.user", COMMERCE_SOURCE));

        Assertions.assertEquals("只读校验未通过：不允许引用系统库 mysql 中的表 user；不允许调用函数 SLEEP",
            exception.getMessage());
    }

    /** NONE：顶层追加 LIMIT maxRows+1，不生成计数。 */
    @Test
    void test_pagingNone() {
        PreparedQuery query = rewrite("SELECT id FROM t_order WHERE status = ? ORDER BY id DESC", List.of("PAID"),
            QueryPaging.none(100));

        Assertions.assertEquals("SELECT id FROM t_order WHERE status = ? ORDER BY id DESC LIMIT 101", query.getSql());
        Assertions.assertEquals(List.of("PAID"), query.getBindings());
        Assertions.assertNull(query.getCountSql());
        Assertions.assertEquals(100, query.getRowLimit());
    }

    /** OFFSET：LIMIT size+1 OFFSET n；计数去掉顶层 ORDER BY，其中的占位符随之移出计数绑定。 */
    @Test
    void test_pagingOffsetWithTotal() {
        PreparedQuery query = rewrite("SELECT id, amount FROM t_order WHERE client_id = ? "
            + "ORDER BY FIELD(status, ?, ?), id", List.of(7L, "PAID", "REFUNDED"), QueryPaging.offset(20, 40, true));

        Assertions.assertEquals("SELECT id, amount FROM t_order WHERE client_id = ? ORDER BY FIELD(status, ?, ?), id "
            + "LIMIT 21 OFFSET 40", query.getSql());
        Assertions.assertEquals(List.of(7L, "PAID", "REFUNDED"), query.getBindings());
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT id, amount FROM t_order WHERE client_id = ?) t",
            query.getCountSql());
        Assertions.assertEquals(List.of(7L), query.getCountBindings());
    }

    /** CURSOR：模板自写游标条件，服务只追加 LIMIT size+1。 */
    @Test
    void test_pagingCursor() {
        PreparedQuery query = rewrite("SELECT id FROM t_order WHERE id < ? ORDER BY id DESC", List.of(500L),
            QueryPaging.cursor(50));

        Assertions.assertEquals("SELECT id FROM t_order WHERE id < ? ORDER BY id DESC LIMIT 51", query.getSql());
        Assertions.assertEquals(List.of(500L), query.getBindings());
    }

    /** UNION 与 WITH 的分页改写作用于整条语句的顶层，绑定顺序保持。 */
    @Test
    void test_pagingUnionAndWith() {
        PreparedQuery union = rewrite("SELECT id FROM t_order WHERE a = ? UNION SELECT id FROM t_refund WHERE b = ? "
            + "ORDER BY id", List.of("A", "B"), QueryPaging.offset(10, 0, true));
        PreparedQuery with = rewrite("WITH x AS (SELECT id FROM t_order WHERE a = ?) SELECT id FROM x WHERE id > ?",
            List.of("A", 9L), QueryPaging.none(10));

        Assertions.assertEquals("SELECT id FROM t_order WHERE a = ? UNION SELECT id FROM t_refund WHERE b = ? "
            + "ORDER BY id LIMIT 11 OFFSET 0", union.getSql());
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT id FROM t_order WHERE a = ? UNION "
            + "SELECT id FROM t_refund WHERE b = ?) t", union.getCountSql());
        Assertions.assertEquals(List.of("A", "B"), union.getCountBindings());
        Assertions.assertEquals("WITH x AS (SELECT id FROM t_order WHERE a = ?) SELECT id FROM x WHERE id > ? LIMIT 11",
            with.getSql());
        Assertions.assertEquals(List.of("A", 9L), with.getBindings());
    }

    /**
     * 断言被拒绝，且第一条原因为给定文本。
     *
     * @param sql SQL
     * @param message 期望的第一条原因
     */
    private void assertRejected(String sql, String message) {
        SqlInspection inspection = guard.inspect(sql, COMMERCE_SOURCE);
        Assertions.assertFalse(inspection.passed(), sql);
        Assertions.assertNull(inspection.getSelect(), sql);
        Assertions.assertEquals(message, inspection.getViolations().getFirst().getMessage(), sql);
    }

    /**
     * 断言在指定默认库的 MySQL 数据源上通过。
     *
     * @param sql SQL
     */
    private void assertPassed(String sql) {
        SqlInspection inspection = guard.inspect(sql, COMMERCE_SOURCE);
        Assertions.assertTrue(inspection.passed(), () -> sql + " → " + messages(inspection));
        Assertions.assertNotNull(inspection.getSelect(), sql);
    }

    /**
     * 先校验再改写。
     *
     * @param sql SQL
     * @param bindings 绑定值
     * @param paging 分页参数
     * @return 改写结果
     */
    private PreparedQuery rewrite(String sql, List<Object> bindings, QueryPaging paging) {
        return rewriter.rewrite(guard.requireReadOnly(sql, COMMERCE_SOURCE), bindings, paging);
    }

    /**
     * 拒绝原因文本。
     *
     * @param inspection 校验结果
     * @return 原因列表
     */
    private static List<String> messages(SqlInspection inspection) {
        return inspection.getViolations().stream().map(RenderedQueryViolation::getMessage).toList();
    }
}
