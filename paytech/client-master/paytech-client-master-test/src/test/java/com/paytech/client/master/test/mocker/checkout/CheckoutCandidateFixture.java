package com.paytech.client.master.test.mocker.checkout;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.foundation.testsupport.api.Clear;

import tools.jackson.databind.json.JsonMapper;

/**
 * 候选查询的合成主档事实（fixtures/client-master/checkout-candidates.json，自原 CCT checkout-query.json 拆出的主档表）。
 * <li>只清理本例成功插入的行；只读断言比较全部合成表的行内容与补偿任务。</li>
 */
@TestComponent
public class CheckoutCandidateFixture implements Clear {
    /** 合成 Client。 */
    public static final long CLIENT_ID = 910000000000001L;
    /** 固定商业时点。 */
    public static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    private final JdbcTemplate jdbc;
    private final List<String[]> inserted = new ArrayList<>();
    private Map<String, List<Map<String, Object>>> rows;

    /** 使用被测应用的同一数据源。 */
    public CheckoutCandidateFixture(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 插入全部合成主档事实；已有同键数据时失败。 */
    @SuppressWarnings("unchecked")
    public void install() {
        assertNull(rows, "前例候选夹具尚未清理");
        try (var input = getClass().getResourceAsStream("/fixtures/client-master/checkout-candidates.json")) {
            rows = JsonMapper.builder().build().readValue(input, Map.class);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        for (var table : rows.entrySet()) {
            for (var row : table.getValue()) {
                insert(table.getKey(), row);
            }
        }
    }

    /** 操作前后合成表与补偿任务均不变，即使业务断言失败仍执行检查。 */
    public void assertReadOnly(Runnable operation) {
        var before = snapshot();
        assertAll(operation::run, () -> assertEquals(before, snapshot(), "候选查询不得修改主档或登记补偿任务"));
    }

    /** 合成表全部行与补偿任务的有序快照。 */
    private List<Object> snapshot() {
        List<Object> result = new ArrayList<>();
        for (String table : rows.keySet()) {
            result.add(jdbc.queryForList("SELECT * FROM t_" + table + " ORDER BY 1"));
        }
        result.add(jdbc.queryForList("SELECT id FROM t_compensation_event ORDER BY id", Long.class));
        return result;
    }

    /** 插入一行并登记清理。 */
    private void insert(String table, Map<String, Object> row) {
        String fields = String.join(",", row.keySet());
        Object[] values = row.values().stream()
            .map(v -> v instanceof String text && text.matches("[0-9]{4}-.*Z") ? Timestamp.from(Instant.parse(text)) : v)
            .toArray();
        jdbc.update("INSERT INTO t_" + table + " (" + fields + ") VALUES ("
            + String.join(",", Collections.nCopies(values.length, "?")) + ")", values);
        String key = row.keySet().iterator().next();
        inserted.add(new String[] {table, key, String.valueOf(row.get(key))});
    }

    /** 逆序删除本例插入的行。 */
    @Override
    public void clear() {
        try {
            for (int i = inserted.size() - 1; i >= 0; i--) {
                String[] row = inserted.get(i);
                jdbc.update("DELETE FROM t_" + row[0] + " WHERE " + row[1] + "=?", row[2]);
            }
        } finally {
            inserted.clear();
            rows = null;
        }
    }
}
