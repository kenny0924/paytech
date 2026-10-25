package com.paytech.compliance.test.infrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在 compliance Spring 测试上下文刷新前验证仓库自有本地 mor_compliance 已由开发者人工准备（由原 RiskDatabasePrecondition 拆出）。
 * <li>表数等于本模块 init/ddl.sql（尽调 20 表、结算控制 3 表、t_compensation_event、t_consumed_event）；隔离级别 REPEATABLE-READ。</li>
 * <li>compliance 不使用 Redis，不做 Redis 前置检查。</li>
 */
public final class ComplianceDatabasePrecondition implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** mor_compliance 的表数：compliance-schema.sql 的 24 张表加 t_consumed_event（= init/ddl.sql 的 CREATE TABLE 数）。 */
    public static final int EXPECTED_TABLE_COUNT = 25;

    /** 不包含凭据的本地数据库准备说明。 */
    public static final String SETUP = "compliance auto 需要仓库 local-environment 的 127.0.0.1:13306/mor_compliance "
        + "（账号 compliance_app）。请先在 local-environment 执行 `docker compose --env-file .env.local --profile base up -d`，"
        + "再由开发者审核后用 MySQL root 人工执行一次 paytech/compliance/paytech-compliance-test/src/test/resources/init/ddl.sql"
        + "（建库、建账号、最小权限授权与全部 CREATE TABLE）；已有库勿重复执行，改为执行 paytech/compliance/docs/database/sql/changes 下"
        + "尚未执行的增量脚本。密码经 COMPLIANCE_TEST_DB_PASSWORD 或 "
        + "MOR_COMPLIANCE_DB_PASSWORD 注入，缺省为 init/ddl.sql 中的仓库本地默认值。测试不会启动容器、建表或灌入数据。";

    private static final String LOOPBACK_URL = "jdbc:mysql://127.0.0.1:13306/mor_compliance"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String USERNAME = "compliance_app";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        validateConnectionTarget(url, username);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(SETUP);
        }
        try (Connection connection = DriverManager.getConnection(url, connectionProperties(username, password))) {
            verifyPreparedDatabase(connection);
        } catch (SQLException | RuntimeException failure) {
            throw new IllegalStateException(SETUP, failure);
        }
    }

    /**
     * 为启动前的裸 JDBC 探测提供有界 Connector/J 超时。
     *
     * @param username 数据库用户名
     * @param password 仅用于本次连接的数据库密码
     * @return 每次调用新建的连接属性
     */
    public static Properties connectionProperties(String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "2000");
        properties.setProperty("socketTimeout", "2000");
        return properties;
    }

    /**
     * 拒绝任何非仓库自有 Loopback compliance 数据库连接。
     *
     * @param url JDBC URL
     * @param username 数据库用户名
     */
    public static void validateConnectionTarget(String url, String username) {
        if (!LOOPBACK_URL.equals(url) || !USERNAME.equals(username)) {
            throw new IllegalStateException("compliance auto 仅允许 127.0.0.1:13306/mor_compliance 和 compliance_app");
        }
    }

    /**
     * 只读验证完整 Schema 与隔离级别。
     *
     * @param connection 已连接到 compliance 测试数据库的专用连接
     * @throws SQLException 读取元数据失败
     */
    public static void verifyPreparedDatabase(Connection connection) throws SQLException {
        require(count(connection, "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE'") == EXPECTED_TABLE_COUNT);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            + "AND table_name IN ('t_settlement_control','t_settlement_control_scope','t_settle_control_state_change')") == 3L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_qualification_decision' AND column_name='decision_evaluation_id'") == 1L);
        // 2026-10-24 资格增量：手工登记依据列与字符型角色标识（sql/changes/2026-10-24-qualification-manual-and-role-id.sql）。
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_qualification_decision' AND column_name='basis_note'") == 1L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name IN ('t_qualification_decision','t_role_qualification') AND column_name='role_id' "
            + "AND data_type='varchar'") == 2L);
        require("REPEATABLE-READ".equals(text(connection, "SELECT @@transaction_isolation")));
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("compliance 数据库前置查询未返回计数");
            }
            return result.getLong(1);
        }
    }

    private static String text(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("compliance 数据库前置查询未返回文本");
            }
            return result.getString(1);
        }
    }

    private static void require(boolean ready) {
        if (!ready) {
            throw new IllegalStateException(SETUP);
        }
    }
}
