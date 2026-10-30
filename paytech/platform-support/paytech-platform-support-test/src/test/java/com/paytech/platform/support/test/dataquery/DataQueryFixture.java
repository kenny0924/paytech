package com.paytech.platform.support.test.dataquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;

import lombok.RequiredArgsConstructor;

/**
 * 本地被查库 mor_data_query_fixture 的连接参数与只读探针（Q12）。
 * <li>库、表与两个账号由 local-environment/mysql/manual/2026-10-22-data-query-fixture.sql 人工准备；缺失时测试立即失败并给出准备方法。</li>
 * <li>只用只读账号读取行数与 CHECKSUM，用于证明测试前后被查库数据不变（AC-06）；不写入、不清理被查库。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class DataQueryFixture {

    /** 被查库名。 */
    public static final String DATABASE = "mor_data_query_fixture";

    /** 退款小表，固定 10 行。 */
    public static final String REFUNDS = DATABASE + ".t_refund_fixture";

    /** Client 小表，固定 3 行。 */
    public static final String CLIENTS = DATABASE + ".t_client_fixture";

    /** 0–999 序列表，固定 1000 行，三表 CROSS JOIN 用于超时测试。 */
    public static final String NUMBERS = DATABASE + ".t_number_fixture";

    private static final Map<String, Long> EXPECTED_ROWS = Map.of(REFUNDS, 10L, CLIENTS, 3L, NUMBERS, 1000L);

    private static final String PREPARE = "被查库未就绪：请按 local-environment/mysql/manual/2026-10-22-data-query-fixture.sql "
        + "文件头的方式人工执行一次（建库 mor_data_query_fixture 与 data_query_fixture_reader / data_query_fixture_writer），"
        + "并在 local-environment/.env.local 设置 MOR_DATA_QUERY_FIXTURE_READER_PASSWORD 与 MOR_DATA_QUERY_FIXTURE_WRITER_PASSWORD";

    private final Environment environment;

    /** 主机。 */
    public String host() {
        return environment.getRequiredProperty("test.data-query.fixture.host");
    }

    /** 端口。 */
    public int port() {
        return environment.getRequiredProperty("test.data-query.fixture.port", Integer.class);
    }

    /** 只读账号。 */
    public String readerUsername() {
        return environment.getRequiredProperty("test.data-query.fixture.reader-username");
    }

    /** 只读账号密码（仓库隔离的本地测试值）。 */
    public String readerPassword() {
        return environment.getRequiredProperty("test.data-query.fixture.reader-password");
    }

    /** 写权限账号。 */
    public String writerUsername() {
        return environment.getRequiredProperty("test.data-query.fixture.writer-username");
    }

    /** 写权限账号密码（仓库隔离的本地测试值）。 */
    public String writerPassword() {
        return environment.getRequiredProperty("test.data-query.fixture.writer-password");
    }

    /** 确认被查库与只读账号可用、固定数据完整；否则失败并给出准备说明。 */
    public void assertReady() {
        Map<String, Long> rows;
        try {
            rows = rowCounts();
        } catch (SQLException failure) {
            fail(PREPARE + "；连接失败: " + failure.getMessage());
            return;
        }
        assertEquals(EXPECTED_ROWS, rows, PREPARE + "；固定数据行数不符");
    }

    /** 联合查询链路表与固定行数（2026-10-29-joint-query-fixture.sql）。 */
    private static final Map<String, Long> JOINT_ROWS = Map.of(DATABASE + ".t_jq_order", 3L,
        DATABASE + ".t_jq_checkout_session", 3L, DATABASE + ".t_jq_refund", 2L, DATABASE + ".t_jq_payment_intent", 2L,
        DATABASE + ".t_jq_payment_attempt", 3L, DATABASE + ".t_jq_payment_action", 3L,
        DATABASE + ".t_jq_billing_fact", 2L);

    private static final String PREPARE_JOINT = "联合查询链路表未就绪：请按 "
        + "local-environment/mysql/manual/2026-10-29-joint-query-fixture.sql 文件头的方式以 root 人工执行一次";

    /** 确认联合查询链路表存在且固定数据完整；否则失败并给出准备说明。 */
    public void assertJointReady() {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String table : JOINT_ROWS.keySet()) {
                counts.put(table, Long.valueOf(scalar(statement, "SELECT COUNT(*) FROM " + table)));
            }
        } catch (SQLException failure) {
            fail(PREPARE_JOINT + "；读取失败: " + failure.getMessage());
            return;
        }
        assertEquals(JOINT_ROWS, counts, PREPARE_JOINT + "；固定数据行数不符");
    }

    /**
     * 三张表的行数与 CHECKSUM，测试前后比较证明数据不变。
     *
     * @return 表名到“行数/校验和”
     */
    public Map<String, String> snapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String table : List.of(REFUNDS, CLIENTS, NUMBERS)) {
                snapshot.put(table, scalar(statement, "SELECT COUNT(*) FROM " + table) + "/"
                    + checksum(statement, table));
            }
        } catch (SQLException failure) {
            fail(PREPARE + "；读取校验和失败: " + failure.getMessage());
        }
        return snapshot;
    }

    /** 单值查询。 */
    static String scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    /** CHECKSUM TABLE 的校验和列。 */
    static String checksum(Statement statement, String table) throws SQLException {
        try (ResultSet rows = statement.executeQuery("CHECKSUM TABLE " + table)) {
            rows.next();
            return rows.getString(2);
        }
    }

    /** 三张表的行数。 */
    Map<String, Long> rowCounts() throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String table : List.of(REFUNDS, CLIENTS, NUMBERS)) {
                try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rows.next();
                    counts.put(table, rows.getLong(1));
                }
            }
        }
        return counts;
    }

    /** 以只读账号建立探针连接。 */
    Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://" + host() + ":" + port() + "/" + DATABASE
            + "?allowPublicKeyRetrieval=true&connectTimeout=2000", readerUsername(), readerPassword());
    }
}
