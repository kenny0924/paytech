package com.paytech.commercial.test.mocker.local.checkout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.sql.Timestamp;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 独立合成业务场景；仅成功插入的本测试行可被清理，不清空共享表。 */
public final class CheckoutFixture implements AutoCloseable {
    public static final long CLIENT_ID = 910000000000001L;
    public static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private final Map<String, List<Map<String, Object>>> rows;
    private final List<Runnable> cleanup = new ArrayList<>();
    /** 加载完整解析 HTTP 场景使用的合成本地事实。 */
    @SuppressWarnings("unchecked")
    public CheckoutFixture() {
        try (var input = getClass().getResourceAsStream("/fixtures/commercial/checkout-query.json")) {
            rows = JsonMapper.builder().build().readValue(input, Map.class);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
    /** 只读观察任务身份集合；不读取 Payload，也不清理其他任务。 */
    public List<Long> compensationEventIds(DataSource source) {
        return new JdbcTemplate(source).queryForList("SELECT id FROM t_compensation_event ORDER BY id", Long.class);
    }
    /** 为当前 HTTP 场景插入独立数据，已有同键事实时失败。 */
    public void install(DataSource source) {
        // 每次连接显式解除只读仅用于 Fixture DML；生产读事务仍设置只读。
        try (Connection connection = source.getConnection()) {
            connection.setReadOnly(false);
            JdbcTemplate jdbc = new JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            for (var table : rows.entrySet())
                for (var row : table.getValue())
                    insert(jdbc, source, table.getKey(), row);
        } catch (java.sql.SQLException exception) {
            close();
            throw new AssertionError("请先人工应用 commercial 测试 DDL（init/ddl.sql），再运行本地 MySQL 测试", exception);
        }
    }
    /** 仅在插入成功后登记该行的清理动作。 */
    private void insert(JdbcTemplate jdbc, DataSource source, String table, Map<String, Object> row) {
        String fields = String.join(",", row.keySet());
        Object[] values = row.values().stream().map(v -> v instanceof String text && text.matches("[0-9]{4}-.*Z")
                ? Timestamp.from(Instant.parse(text))
                : v).toArray();
        jdbc.update("INSERT INTO t_" + table + " (" + fields + ") VALUES ("
                + String.join(",", Collections.nCopies(values.length, "?")) + ")", values);
        String key = row.keySet().iterator().next();
        Object id = row.get(key);
        cleanup.add(() -> {
            try (Connection connection = source.getConnection()) {
                connection.setReadOnly(false);
                new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true))
                        .update("DELETE FROM t_" + table + " WHERE " + key + "=?", id);
            } catch (java.sql.SQLException exception) {
                throw new AssertionError(exception);
            }
        });
    }
    /** 逆序清理本例成功插入的行，保留其他开发数据。 */
    @Override
    public void close() {
        for (int i = cleanup.size() - 1; i >= 0; i--)
            cleanup.get(i).run();
        cleanup.clear();
    }
}
