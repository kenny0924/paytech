package com.paytech.migration.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * 只读断言 Foundation 测试库中的记录表与测试表。
 *
 * @author Claude
 */
public final class MigrationRecordChecker {

    private final FoundationTestDatabase database;

    /**
     * 绑定测试库。
     *
     * @param database 测试库
     */
    public MigrationRecordChecker(FoundationTestDatabase database) {
        this.database = database;
    }

    /**
     * 断言 {@code t_schema_migration} 的全部记录，格式为 {@code V<version>:<script>:<success>}。
     *
     * @param expected 按版本升序的预期记录
     * @return 本 Checker
     */
    public MigrationRecordChecker migrations(String... expected) {
        Assertions.assertEquals(List.of(expected), strings("SELECT CONCAT('V', version, ':', script, ':', success) "
            + "FROM t_schema_migration ORDER BY version"));
        return this;
    }

    /**
     * 断言某版本失败记录的错误摘要包含文本。
     *
     * @param version 版本号
     * @param text 文本
     * @return 本 Checker
     */
    public MigrationRecordChecker errorContains(int version, String text) {
        List<String> messages = strings("SELECT error_message FROM t_schema_migration WHERE version = " + version);
        Assertions.assertEquals(1, messages.size());
        Assertions.assertTrue(messages.get(0).contains(text), messages.get(0));
        return this;
    }

    /**
     * 断言 {@code t_schema_seed} 的全部记录文件名。
     *
     * @param expected 预期文件名
     * @return 本 Checker
     */
    public MigrationRecordChecker seeds(String... expected) {
        Assertions.assertEquals(List.of(expected), strings("SELECT seed_file FROM t_schema_seed ORDER BY seed_file"));
        return this;
    }

    /**
     * 断言本模块拥有的表恰为预期集合。
     *
     * @param expected 按名称升序的表名
     * @return 本 Checker
     */
    public MigrationRecordChecker tables(String... expected) {
        try (Connection connection = database.openConnection()) {
            Assertions.assertEquals(List.of(expected), FoundationTestDatabase.ownedTables(connection));
        } catch (SQLException exception) {
            Assertions.fail(exception);
        }
        return this;
    }

    /**
     * 执行只读查询并按第一列返回字符串。
     *
     * @param sql 查询
     * @return 每行第一列
     */
    public List<String> strings(String sql) {
        List<String> values = new ArrayList<>();
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        } catch (SQLException exception) {
            Assertions.fail(exception);
        }
        return values;
    }
}
