package com.paytech.migration.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

/**
 * local-environment 中的 Foundation 测试库 {@value #DATABASE}（库名与账号见
 * {@code local-environment/mysql/init/01-create-databases-and-users.sh}）。
 *
 * <p>
 * 本模块测试只在该库中创建 {@value #TEST_TABLE_PREFIX} 前缀表与执行器的两张记录表，并在每例前后删除它们；不触碰其他表。
 * 密码只从 {@code <paytech.local-environment.dir>/.env.local} 的 {@value #PASSWORD_KEY} 读取，不打印。
 *
 * @author Claude
 */
public final class FoundationTestDatabase {

    /**
     * Foundation 测试库名。
     */
    public static final String DATABASE = "paytech_foundation_test";

    /**
     * 测试表前缀。
     */
    public static final String TEST_TABLE_PREFIX = "t_mr_test_";

    private static final String USER = "foundation_test";

    private static final String PASSWORD_KEY = "PAYTECH_FOUNDATION_TEST_DB_PASSWORD";

    private static final String URL = "jdbc:mysql://127.0.0.1:13306/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000";

    private static final List<String> RUNNER_TABLES = List.of("t_schema_migration", "t_schema_seed");

    private static final String SETUP_HINT = "请先手工启动仓库 local-environment 的 MySQL："
        + "cd local-environment && docker compose --env-file .env.local up -d mysql；"
        + "并确认 .env.local 含 " + PASSWORD_KEY + "、库 " + DATABASE + " 与账号 " + USER + " 已由 mysql/init 脚本创建；"
        + "在 git worktree 中运行时用 -Dpaytech.local-environment.dir=<含 .env.local 的 local-environment 目录> 指定。";

    private final String password;

    private FoundationTestDatabase(String password) {
        this.password = password;
    }

    /**
     * 读取凭据并确认数据库可连接；任何前置条件缺失都以带启动说明的失败结束测试，不跳过。
     *
     * @return 测试库
     */
    public static FoundationTestDatabase connect() {
        String directory = System.getProperty("paytech.local-environment.dir");
        if (directory == null || directory.isBlank()) {
            Assertions.fail("缺少系统属性 paytech.local-environment.dir（由 paytech-parent 的 Surefire 配置传入）。" + SETUP_HINT);
        }
        FoundationTestDatabase database = new FoundationTestDatabase(readPassword(Path.of(directory, ".env.local")));
        try (Connection connection = database.openConnection()) {
            Assertions.assertEquals(DATABASE, connection.getCatalog());
        } catch (SQLException exception) {
            Assertions.fail("无法连接 Foundation 测试库 127.0.0.1:13306/" + DATABASE + " [" + exception.getSQLState()
                + "]: " + exception.getMessage() + "。" + SETUP_HINT);
        }
        return database;
    }

    /**
     * 执行器所需的环境变量：ledger 服务指向 Foundation 测试库，并声明物理库名覆盖。
     *
     * @return 环境变量
     */
    public Map<String, String> runnerEnvironment() {
        return Map.of("PAYTECH_DB_URL_LEDGER", URL, "PAYTECH_DB_USER_LEDGER", USER,
            "PAYTECH_DB_PASSWORD_LEDGER", password, "PAYTECH_DB_SCHEMA_LEDGER", DATABASE);
    }

    /**
     * 打开一个测试库连接，供清理与 Checker 只读断言使用。
     *
     * @return 连接
     * @throws SQLException 连接失败
     */
    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, password);
    }

    /**
     * 删除本模块拥有的全部表：{@value #TEST_TABLE_PREFIX} 前缀表与两张记录表。
     *
     * @return 删除失败说明；全部成功为空
     */
    public List<String> dropOwnedTables() {
        List<String> failures = new ArrayList<>();
        try (Connection connection = openConnection()) {
            for (String table : ownedTables(connection)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                } catch (SQLException exception) {
                    failures.add(table + ": " + exception.getMessage());
                }
            }
        } catch (SQLException exception) {
            failures.add("连接失败: " + exception.getMessage());
        }
        return failures;
    }

    /**
     * 当前存在的本模块拥有的表。
     *
     * @param connection 连接
     * @return 表名
     * @throws SQLException 数据库错误
     */
    public static List<String> ownedTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT TABLE_NAME FROM information_schema.TABLES "
            + "WHERE TABLE_SCHEMA = DATABASE() AND (TABLE_NAME LIKE 't\\_mr\\_test\\_%' OR TABLE_NAME IN (?, ?)) "
            + "ORDER BY TABLE_NAME")) {
            statement.setString(1, RUNNER_TABLES.get(0));
            statement.setString(2, RUNNER_TABLES.get(1));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
        }
        return tables;
    }

    /**
     * 从 .env.local 读取测试库密码。
     *
     * @param envFile .env.local
     * @return 密码
     */
    private static String readPassword(Path envFile) {
        if (!Files.isRegularFile(envFile)) {
            Assertions.fail("缺少 " + envFile + "。" + SETUP_HINT);
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                if (line.startsWith(PASSWORD_KEY + "=")) {
                    return line.substring(PASSWORD_KEY.length() + 1).trim();
                }
            }
        } catch (IOException exception) {
            Assertions.fail("无法读取 " + envFile + ": " + exception.getMessage());
        }
        return Assertions.fail(envFile + " 中没有 " + PASSWORD_KEY + "。" + SETUP_HINT);
    }
}
