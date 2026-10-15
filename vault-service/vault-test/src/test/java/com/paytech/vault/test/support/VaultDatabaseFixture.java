package com.paytech.vault.test.support;

import com.paytech.vault.client.*;
import org.slf4j.MDC;
import java.sql.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** 仅连接已手工准备的 Vault 库；SQL 错误不进入保留报告。 */
public final class VaultDatabaseFixture implements AutoCloseable {
    private static final String JDBC = "jdbc:mysql://127.0.0.1:13306/mor_vault";
    private static final String OWN_REQUEST = "caller_service IN (?, ?) AND idempotency_key LIKE ?";
    private static final String OWN_EVENT = "caller_service IN (?, ?) AND request_id LIKE ?";
    private final String prefix = "e2e-" + UUID.randomUUID().toString().replace("-", "");
    private final AtomicInteger attempts = new AtomicInteger();
    private final Set<String> sensitive = ConcurrentHashMap.newKeySet();
    private final Connection connection;
    private ServerSocket nodeLease;
    private boolean preserveRows;
    private boolean closed;

    public VaultDatabaseFixture() {
        assertTrue(JDBC.equals(required("VAULT_TEST_JDBC_URL")), "仅允许指定的本地 Vault JDBC 地址");
        assertTrue("vault_app".equals(required("VAULT_TEST_JDBC_USER")), "仅允许 Vault 专属测试账号");
        String password = required("VAULT_TEST_JDBC_PASSWORD");
        assertTrue(required("VAULT_TEST_DB_DSN").equals("vault_app:" + password
                + "@tcp(127.0.0.1:13306)/mor_vault?parseTime=true&loc=UTC"), "Go 与 Java 必须使用同一专属本地 Vault 凭据");
        sensitive.add(password);
        sensitive.add(canary());
        var properties = new Properties();
        properties.setProperty("user", "vault_app");
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "5000");
        try {
            connection = DriverManager.getConnection(JDBC, properties);
        } catch (SQLException exception) {
            throw new AssertionError("请准备 local-environment MySQL、Vault 专属凭据和手工三表 SQL");
        }
        try { validateSchema(); } catch (Throwable failure) { closeConnection(); throw failure; }
    }

    public static String required(String name) {
        String value = System.getenv(name);
        assertTrue(value != null && !value.isEmpty(), "缺少显式测试变量：" + name);
        return value;
    }

    private void validateSchema() {
        assertEquals(6, count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
        assertEquals(64, count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()"));
        for (String table : List.of("t_vault_record", "t_vault_protect_request", "t_vault_access_event",
                "t_vault_data_type", "t_vault_access_rule", "t_vault_policy_change")) {
            count("SELECT COUNT(*) FROM " + table + " WHERE 1=0");
        }
    }

    public String caller(String suffix) { return prefix + "-" + suffix; }
    /** 本次运行专属的数据类型编码，避免与共享库中的其他策略冲突。 */
    public String type(String base) { return "E2E_" + prefix.substring(4).toUpperCase(Locale.ROOT) + "_" + base; }
    public String key(String suffix) { return prefix + "-" + suffix; }
    public String requestId() { return key("r" + attempts.incrementAndGet()); }
    public String canary() { return "VAULT-E2E-SENSITIVE-CANARY-" + prefix; }
    /** 本次运行的运营账号 ID；策略变更按它清理。 */
    public String operator() { return prefix + "-op"; }
    public Set<String> sensitiveValues() { return Set.copyOf(sensitive); }
    public void remember(ProtectResult result) { sensitive.add(result.token()); }
    public void preserveRows() { preserveRows = true; }

    void acquireNodeLease() throws IOException {
        assertNull(nodeLease, "同一数据库 Fixture 不允许启动多个 Go 节点");
        nodeLease = new ServerSocket(19128, 1, InetAddress.getByName("127.0.0.1"));
    }

    public ProtectCommand command(String key, String type, TokenMode mode, String value) {
        sensitive.add(value);
        return new ProtectCommand(type, mode, value, "TEST", key(key));
    }

    public <T> T call(Supplier<T> operation) {
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId());
        try { return operation.get(); }
        finally { if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous); }
    }

    public void assertCounts(int records, int requests, int events) {
        assertEquals(records, count("SELECT COUNT(DISTINCT record_id) FROM t_vault_protect_request WHERE " + OWN_REQUEST, ownership()));
        assertEquals(requests, count("SELECT COUNT(*) FROM t_vault_protect_request WHERE " + OWN_REQUEST, ownership()));
        assertEquals(events, count("SELECT COUNT(*) FROM t_vault_access_event WHERE " + OWN_EVENT, ownership()));
    }

    public void assertAudit(String action, String outcome, String code, int count) {
        assertEquals(count, count("SELECT COUNT(*) FROM t_vault_access_event WHERE " + OWN_EVENT
                + " AND action=? AND outcome=? AND result_code=?", caller("a"), caller("b"), prefix + "-%", action, outcome, code));
    }

    private Object[] ownership() { return new Object[]{caller("a"), caller("b"), prefix + "-%"}; }

    private int count(String sql, Object... arguments) {
        try (var statement = prepare(sql, arguments); var result = statement.executeQuery()) {
            assertTrue(result.next(), "Vault 数据库计数未返回");
            return result.getInt(1);
        } catch (SQLException exception) { throw new AssertionError("Vault 数据库只读断言失败；不输出 SQL 异常"); }
    }

    private PreparedStatement prepare(String sql, Object... arguments) throws SQLException {
        var statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(5);
        for (int index = 0; index < arguments.length; index++) statement.setObject(index + 1, arguments[index]);
        return statement;
    }

    /** 在 Go 进程启动前写入本次运行的数据类型与规则（Vault 只从数据库读取策略）。 */
    void seedPolicy() {
        try {
            connection.setAutoCommit(false);
            var types = Map.of("REFERENCE_VALUE", "REFERENCE", "STABLE_VALUE", "STABLE", "OTHER_VALUE", "STABLE");
            for (var type : types.entrySet()) {
                delete("INSERT INTO t_vault_data_type (data_type_id,data_type_code,token_mode,data_class,description,manual_protect_enabled,"
                        + "manual_reveal_enabled,status,version,create_time,update_time) VALUES (?,?,?,'STANDARD','vault-test',0,0,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                        randomId(), type(type.getKey()), type.getValue());
            }
            for (String suffix : List.of("a", "b")) {
                for (String action : List.of("PROTECT", "REVEAL")) {
                    for (String type : types.keySet()) {
                        delete("INSERT INTO t_vault_access_rule (access_rule_id,caller_service,action,data_type_code,purpose_code,status,version,"
                                + "create_time,update_time) VALUES (?,?,?,?,'TEST','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                                randomId(), caller(suffix), action, type(type));
                    }
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { /* 关闭连接释放失败事务。 */ }
            throw new AssertionError("写入本次运行的 Vault 策略失败；请确认已执行策略表 DDL");
        }
    }

    private static long randomId() { return java.util.concurrent.ThreadLocalRandom.current().nextLong(1L << 40, Long.MAX_VALUE); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (preserveRows) { closeConnection(); return; } // 未确认子进程死亡：不释放节点租约。
        try {
            connection.setAutoCommit(false);
            var candidates = candidates(); // 删除请求前冻结 ID + 不可变 Token，绝不只按 Snowflake ID 清理。
            delete("DELETE FROM t_vault_access_event WHERE " + OWN_EVENT, ownership());
            delete("DELETE FROM t_vault_protect_request WHERE " + OWN_REQUEST, ownership());
            delete("DELETE FROM t_vault_access_rule WHERE caller_service IN (?, ?)", caller("a"), caller("b"));
            delete("DELETE FROM t_vault_policy_change WHERE actor_id = ?", operator());
            delete("DELETE FROM t_vault_data_type WHERE data_type_code LIKE ?", type("%"));
            for (var candidate : candidates.entrySet()) {
                delete("DELETE FROM t_vault_record WHERE record_id=? AND token=?"
                        + " AND NOT EXISTS (SELECT 1 FROM t_vault_protect_request p WHERE p.record_id=t_vault_record.record_id)"
                        + " AND NOT EXISTS (SELECT 1 FROM t_vault_access_event e WHERE e.record_id=t_vault_record.record_id)",
                        candidate.getKey(), candidate.getValue());
            }
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) { /* 关闭连接释放失败事务。 */ }
            throw new AssertionError("Vault 本次拥有数据清理失败；请人工检查，不输出 SQL 异常");
        } finally {
            closeConnection(); // 连接关闭失败也保留租约，不让下一节点进入未确认的事务边界。
            releaseNodeLease();
        }
    }

    private void releaseNodeLease() {
        try { if (nodeLease != null) nodeLease.close(); }
        catch (IOException exception) { throw new AssertionError("Vault 节点互斥端口释放失败"); }
    }

    private Map<Long, String> candidates() throws SQLException {
        var candidates = new HashMap<Long, String>();
        try (var statement = prepare("SELECT DISTINCT r.record_id,r.token FROM t_vault_record r"
                + " JOIN t_vault_protect_request p ON p.record_id=r.record_id WHERE " + OWN_REQUEST, ownership());
             var result = statement.executeQuery()) {
            while (result.next()) candidates.put(result.getLong(1), result.getString(2));
        }
        return candidates;
    }

    private void delete(String sql, Object... arguments) throws SQLException {
        try (var statement = prepare(sql, arguments)) { statement.executeUpdate(); }
    }

    private void closeConnection() {
        try { connection.close(); } catch (SQLException exception) { throw new AssertionError("Vault 测试连接关闭失败"); }
    }
}
