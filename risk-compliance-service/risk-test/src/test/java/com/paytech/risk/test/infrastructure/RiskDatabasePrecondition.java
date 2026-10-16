package com.paytech.risk.test.infrastructure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在 Risk Spring 测试上下文刷新前验证仓库自有本地 MySQL 已由开发者人工准备。
 */
public final class RiskDatabasePrecondition
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Risk 当前获批物理模型中的表数。 */
    public static final int EXPECTED_TABLE_COUNT = 85;

    /**
     * 不包含凭据的本地数据库准备说明。
     */
    public static final String SETUP = "Risk auto 需要仓库 local-environment 的 "
        + "127.0.0.1:13306/mor_risk。请先在 local-environment 执行 "
        + "`docker compose --env-file .env.local --profile base up -d`，再回到 risk-compliance-service "
        + "并由开发者审核后依次执行 "
        + "`mysql -h 127.0.0.1 -P 13306 -u risk_app -p mor_risk "
        + "< docs/database/sql/risk-compliance-schema.sql` 与 "
        + "`mysql -h 127.0.0.1 -P 13306 -u risk_app -p mor_risk "
        + "< docs/database/sql/risk-compliance-initial-data.sql`，最后通过 RISK_TEST_DB_PASSWORD "
        + "或 MOR_RISK_DB_PASSWORD 注入密码。已有旧库按当前迁移记录应用所需增量；规则集根历史增量见 ../requirements/20260911/operations-console-风控规则集列表优化/db/risk-compliance-service/；本轮字典增量见 ../requirements/20260911/operations-console-风控规则集历史与字典管理/db/risk-compliance-service/ddl.sql 与 dml.sql，必须人工备份审核后执行；禁止重跑全量初始化。决策流增量见 ../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/ddl.sql；仅增加四表，须经授权人工应用。规则输入解耦的三表增量见 ../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/simplification/local-application.md，需独立授权人工应用。统一变量增量见 ../requirements/20260912/operations-console-统一风控变量管理与条件编排/db/risk-compliance-service/local-application.md；新增两表、四张旧表中增加五列并调整四列注释及26行目录，须经本轮单独授权人工应用。支付反欺诈一期增量见 ../requirements/20261014/risk-支付反欺诈一期/db/ddl.sql 与 dml.sql；新增两表、改五表并初始化支付决策点、动作与变量，须经授权人工应用。评估流程轨迹增量见 ../requirements/20261015/risk-评估结果查询与解释/db/ddl.sql；只新增一表，须经授权人工应用。结账点运行期评估增量见 ../requirements/20261016/risk-结账点运行期评估/db/ddl.sql 与 dml.sql；重建结账输入快照表、结账变量迁到 ORDER_PREPARATION v5 并初始化结账运行期点版本，须经授权人工应用。已有表不得重跑完整 Schema。本轮还需要本机 Redis 16379，通过 RISK_TEST_REDIS_PASSWORD 或 MOR_REDIS_PASSWORD 注入凭据。测试不会启动容器、建表或灌入数据。";

    private static final String LOOPBACK_URL =
        "jdbc:mysql://127.0.0.1:13306/mor_risk"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USERNAME = "risk_app";
    private static final List<ActionCatalogEntry> ACTIONS = List.of(
        new ActionCatalogEntry("ALLOW_CHECKOUT", "DECISION_ALLOW_V1", "ALLOW"),
        new ActionCatalogEntry("REVIEW_CHECKOUT", "DECISION_REVIEW_V1", "REVIEW"),
        new ActionCatalogEntry("DENY_CHECKOUT", "DECISION_DENY_V1", "DENY")
    );

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        validateConnectionTarget(url, username);
        verifyRedis(environment);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(SETUP);
        }

        try (Connection connection = DriverManager.getConnection(
            url, connectionProperties(username, password))) {
            verifyPreparedDatabase(connection);
        } catch (SQLException | RuntimeException failure) {
            throw new IllegalStateException(SETUP, failure);
        }
    }

    /** 只读验证仓库本机 Redis，连接与读取均有界，错误信息不包含凭据或协议回显。 */
    private static void verifyRedis(ConfigurableEnvironment environment) {
        String host = environment.getProperty("spring.data.redis.host");
        int port = environment.getProperty("spring.data.redis.port", Integer.class, 0);
        String password = environment.getProperty("spring.data.redis.password");
        if (!"127.0.0.1".equals(host) || port != 16379 || password == null || password.isBlank()) {
            throw new IllegalStateException(SETUP);
        }
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(2000);
            var output = socket.getOutputStream();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8));
            byte[] secret = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.write(("*2\r\n$4\r\nAUTH\r\n$" + secret.length + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write(secret); output.write("\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); output.flush();
            if (!"+OK".equals(reader.readLine())) throw new java.io.IOException("Redis AUTH 未通过");
            output.write("*1\r\n$4\r\nPING\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); output.flush();
            if (!"+PONG".equals(reader.readLine())) throw new java.io.IOException("Redis PING 未通过");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(SETUP, failure);
        }
    }

    /**
     * 为启动前的裸 JDBC 探测提供有界 Connector/J 超时。
     *
     * <p>该探测发生在 DataSource/Hikari 创建之前，因此不能依赖 Hikari 的连接属性。</p>
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
     * 拒绝任何非仓库自有 Loopback Risk 数据库连接。
     *
     * @param url JDBC URL
     * @param username 数据库用户名
     */
    public static void validateConnectionTarget(String url, String username) {
        if (!LOOPBACK_URL.equals(url) || !USERNAME.equals(username)) {
            throw new IllegalStateException("Risk auto 仅允许 127.0.0.1:13306/mor_risk 和 risk_app");
        }
    }

    /**
     * 只读验证完整 Schema、执行目录和仅配置动作目录。
     *
     * @param connection 已连接到 Risk 测试数据库的专用连接
     * @throws SQLException 读取元数据或目录失败
     */
    public static void verifyPreparedDatabase(Connection connection) throws SQLException {
        require(count(connection, "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE'") == EXPECTED_TABLE_COUNT);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_rule_set' AND column_name IN ('rule_set_id','decision_point_id','rule_set_code',"
            + "'current_rule_set_version_id','status','version','deleted_by','deleted_time','delete_reason_code',"
            + "'delete_trigger_id','create_time','update_time')") == 12L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name IN ('t_rule_reject_reason','t_rule_subject_tag')") == 21L);
        require(count(connection, "SELECT COUNT(DISTINCT table_name,index_name) FROM information_schema.statistics "
            + "WHERE table_schema=DATABASE() AND ((table_name='t_risk_rule' AND index_name='idx_rule_reason') "
            + "OR (table_name='t_rule_action_parameter' AND index_name='idx_rule_param_string'))") == 2L);
        require(count(connection, "SELECT COUNT(*) FROM t_decision_point") == 2L);
        require(count(connection, "SELECT COUNT(*) FROM t_decision_point WHERE point_code='CHECKOUT_PREPARE' "
            + "AND business_domain='CHECKOUT' AND status='ACTIVE'") == 1L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_command_idempotency' AND column_name='idempotency_key' "
            + "AND character_set_name='ascii' AND collation_name='ascii_bin'") == 1L);
        require("REPEATABLE-READ".equals(text(connection, "SELECT @@transaction_isolation")));
        require(count(connection, "SELECT COUNT(*) FROM t_action_version") == ACTIONS.size() + 2 + 3);
        require(count(connection, "SELECT COUNT(*) FROM t_action_version WHERE action_version_id IN (240004,240005) "
            + "AND status='CONFIGURATION_ONLY' AND handler_code IS NULL AND implementation_version IS NULL "
            + "AND implementation_hash IS NULL AND terminal_flag=0") == 2);
        require(count(connection, "SELECT COUNT(*) FROM t_action_parameter_definition WHERE action_version_id IN (240004,240005)") == 4);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND ((table_name='t_rule_set_version' AND column_name='content_format') "
            + "OR (table_name='t_risk_rule' AND column_name='reason_message') "
            + "OR (table_name='t_rule_action' AND column_name='target_binding_code'))") == 3);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND ((table_name='t_rule_set_version' AND column_name IN ('input_contract_code','input_contract_version') AND is_nullable='NO') "
            + "OR (table_name IN ('t_rule_operand','t_rule_action_parameter') AND column_name='field_code' AND is_nullable='YES'))") == 4L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name IN ('t_rule_operand','t_rule_action_parameter') AND column_name='input_field_id'") == 0L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name IN ('t_risk_variable','t_risk_variable_member')") == 42L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND ((table_name='t_rule_operand' AND column_name IN ('variable_code','numeric_unit','currency_code','currency_variable_code')) "
            + "OR (table_name='t_rule_action_parameter' AND column_name='variable_code'))") == 5L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable WHERE variable_id BETWEEN 900000000000091001 AND 900000000000091020") == 20L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable_member WHERE member_id BETWEEN 900000000000092001 AND 900000000000092009") == 9L);
        for (ActionCatalogEntry action : ACTIONS) {
            require(countAction(connection, action) == 1L);
        }
        verifyPaymentFraudPhaseOne(connection);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_evaluation_flow_trace'") == 11L);
        verifyCheckoutRuntime(connection);
    }

    /** 结账点运行期评估的快照表结构、v5 结账变量与结账运行期点版本。 */
    private static void verifyCheckoutRuntime(Connection connection) throws SQLException {
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_checkout_input_snapshot'") == 17L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable WHERE variable_id BETWEEN 900000000000091001 "
            + "AND 900000000000091020 AND point_code='CHECKOUT_PREPARE' AND source_contract_code='ORDER_PREPARATION' "
            + "AND source_contract_version=5") == 12L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable WHERE variable_id=900000000000091002 "
            + "AND source_contract_version=4 AND status='DISABLED'") == 1L);
        require(count(connection, "SELECT COUNT(*) FROM t_decision_point_version WHERE point_version_id=220001 "
            + "AND decision_point_id=210001 AND version_no=100 AND input_contract_version=5 AND status='FROZEN'") == 1L);
    }

    /** 支付反欺诈一期的结构增量与支付决策点初始化目录。 */
    private static void verifyPaymentFraudPhaseOne(Connection connection) throws SQLException {
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND ((table_name='t_decision_release' AND column_name='decision_flow_version_id') "
            + "OR (table_name='t_risk_variable' AND column_name='feature_version_id') "
            + "OR (table_name='t_risk_list' AND column_name='list_purpose') "
            + "OR (table_name='t_risk_list_entry' AND column_name='source_type' ) "
            + "OR (table_name='t_transaction_risk_assessment' AND column_name='stable_input_hash'))") == 5L);
        require(count(connection, "SELECT COUNT(*) FROM t_decision_point WHERE decision_point_id=210002 "
            + "AND point_code='PAYMENT_ATTEMPT_RISK' AND business_domain='PAYMENT' AND status='ACTIVE'") == 1L);
        require(count(connection, "SELECT COUNT(*) FROM t_decision_point_version WHERE point_version_id=220002 "
            + "AND status='FROZEN'") == 1L);
        require(count(connection, "SELECT COUNT(*) FROM t_action_version WHERE action_version_id BETWEEN 240006 AND 240008 "
            + "AND status='ACTIVE' AND terminal_flag=1 AND action_code IN ('ALLOW_PAYMENT','REVIEW_PAYMENT','DENY_PAYMENT')") == 3L);
        require(count(connection, "SELECT COUNT(*) FROM t_action_version WHERE action_code='ALLOW_PAYMENT_WITH_THREE_DS'") == 0L);
        require(count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='t_decision_flow_end_output'") == 6L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable WHERE variable_id BETWEEN 900000000000093001 AND 900000000000093013") == 13L);
        require(count(connection, "SELECT COUNT(*) FROM t_risk_variable_member WHERE member_id BETWEEN 900000000000094001 AND 900000000000094003") == 3L);
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Risk 数据库前置查询未返回计数");
            }
            return result.getLong(1);
        }
    }

    private static String text(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Risk 数据库前置查询未返回文本");
            }
            return result.getString(1);
        }
    }

    private static long countAction(Connection connection, ActionCatalogEntry action) throws SQLException {
        String sql = "SELECT COUNT(*) FROM t_action_version WHERE action_code=? AND action_category='DECISION' "
            + "AND version_no=1 AND handler_code=? AND implementation_version='1.0.0' "
            + "AND implementation_hash REGEXP '^[0-9a-f]{64}$' AND terminal_flag=1 AND decision_code=? "
            + "AND merge_policy='MOST_RESTRICTIVE' AND shadow_supported_flag=1 AND status='ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, action.actionCode());
            statement.setString(2, action.handlerCode());
            statement.setString(3, action.decisionCode());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Risk Action 目录前置查询未返回计数");
                }
                return result.getLong(1);
            }
        }
    }

    private static void require(boolean ready) {
        if (!ready) {
            throw new IllegalStateException(SETUP);
        }
    }

    private record ActionCatalogEntry(String actionCode, String handlerCode, String decisionCode) {
    }
}
