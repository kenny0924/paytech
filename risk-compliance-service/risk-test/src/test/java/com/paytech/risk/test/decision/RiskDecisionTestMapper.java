package com.paytech.risk.test.decision;

import com.paytech.risk.test.infrastructure.RiskDatabasePrecondition;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 集中准备、观察并精确清理 Risk 决策自动测试拥有的数据库 Fixture。
 */
public final class RiskDecisionTestMapper {

    private static final String OWNED_KEY_PREFIX = "risk-auto-";

    private final JdbcTemplate jdbc;

    public RiskDecisionTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 生成本次测试独占的业务 Code/Key 命名空间。
     *
     * @return 小写、可持久化的唯一测试前缀
     */
    public String uniqueNamespace() {
        return OWNED_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成当前测试独占且满足业务格式的 Rule Set Code。
     *
     * @return 大写测试业务 Code
     */
    public String uniqueRuleSetCode() {
        return "AUTO_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 再次确认当前连接只观察到完整 Schema 和稳定目录。
     */
    public void requirePreparedDatabase() {
        try (var connection = jdbc.getDataSource().getConnection()) {
            RiskDatabasePrecondition.verifyPreparedDatabase(connection);
        } catch (Exception failure) {
            throw new IllegalStateException(RiskDatabasePrecondition.SETUP, failure);
        }
    }

    /**
     * 准备一个由当前测试独占的服务级命令幂等 Fixture。
     */
    public void insertOwnedCommand(
        long id,
        String callerScope,
        String operationType,
        String idempotencyKey,
        String requestHash,
        String responseReference,
        String status,
        Instant expireTime,
        Instant currentTime
    ) {
        requireOwnedKey(idempotencyKey);
        jdbc.update("INSERT INTO t_command_idempotency(idempotency_id,caller_scope,operation_type,idempotency_key,"
            + "request_hash,response_ref,status,expire_time,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
            id, callerScope, operationType, idempotencyKey, requestHash, responseReference, status,
            Timestamp.from(expireTime), Timestamp.from(currentTime), Timestamp.from(currentTime));
    }

    /**
     * 观察准确三元业务键对应的幂等记录数。
     */
    public java.util.Map<String, Long> runtimeFactCounts() {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String table : List.of("t_decision_evaluation", "t_evaluation_action_intent", "t_action_effect", "t_risk_restriction", "t_compensation_event"))
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return java.util.Map.copyOf(counts);
    }

    public long ownedCommandCount(String callerScope, String operationType, String idempotencyKey) {
        requireOwnedKey(idempotencyKey);
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_command_idempotency "
            + "WHERE caller_scope=? AND operation_type=? AND idempotency_key=?", Long.class,
            callerScope, operationType, idempotencyKey);
    }

    /**
     * 观察准确三元业务键，且强制按原始字节区分大小写。
     */
    public long ownedCommandBinaryCount(String callerScope, String operationType, String idempotencyKey) {
        requireOwnedKey(idempotencyKey);
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_command_idempotency "
            + "WHERE caller_scope=? AND operation_type=? AND BINARY idempotency_key=BINARY ?", Long.class,
            callerScope, operationType, idempotencyKey);
    }

    /**
     * 只删除当前测试明确拥有的准确幂等业务键，不做全表清理。
     */
    public void deleteOwnedCommand(String callerScope, String operationType, String idempotencyKey) {
        requireOwnedKey(idempotencyKey);
        jdbc.update("DELETE FROM t_command_idempotency "
            + "WHERE caller_scope=? AND operation_type=? AND idempotency_key=?",
            callerScope, operationType, idempotencyKey);
    }

    /**
     * 持有指定 Decision Point 根行锁，供真实并发请求在生产锁边界会合。
     */
    public DecisionPointLock lockDecisionPoint(String pointCode) {
        if (!"CHECKOUT_PREPARE".equals(pointCode)) {
            throw new IllegalArgumentException("只允许锁定自动测试使用的 Decision Point");
        }
        try {
            Connection connection = jdbc.getDataSource().getConnection();
            connection.setAutoCommit(false);
            PreparedStatement statement = connection.prepareStatement(
                "SELECT decision_point_id FROM t_decision_point WHERE point_code=? FOR UPDATE");
            statement.setString(1, pointCode);
            var result = statement.executeQuery();
            if (!result.next() || result.getLong(1) <= 0 || result.next()) {
                result.close();
                statement.close();
                connection.rollback();
                connection.close();
                throw new IllegalStateException("Decision Point 测试锁目标不唯一");
            }
            result.close();
            statement.close();
            return new DecisionPointLock(connection);
        } catch (Exception failure) {
            throw new IllegalStateException("无法取得 Decision Point 测试锁", failure);
        }
    }

    /**
     * 等待指定数量的请求真正占用连接，避免只验证线程调度而未进入数据库事务。
     */
    public void awaitActiveConnectionsAtLeast(int expected, Duration timeout) {
        HikariDataSource dataSource;
        try {
            dataSource = jdbc.getDataSource().unwrap(HikariDataSource.class);
        } catch (Exception failure) {
            throw new IllegalStateException("无法观察 Risk auto 数据库连接池", failure);
        }
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (dataSource.getHikariPoolMXBean().getActiveConnections() >= expected) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        throw new IllegalStateException("并发请求未在期限内进入数据库事务");
    }

    /**
     * 观察指定测试 Rule Set 的不可变快照行数。
     */
    public DecisionSnapshotCounts snapshotCounts(String ruleSetCode) {
        requireOwnedRuleSetCode(ruleSetCode);
        return new DecisionSnapshotCounts(
            count("SELECT COUNT(*) FROM t_rule_set_version WHERE rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_decision_point_version pv JOIN t_decision_point_rule_set prs "
                + "ON prs.point_version_id=pv.point_version_id JOIN t_rule_set_version rsv "
                + "ON rsv.rule_set_version_id=prs.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_decision_point_input_field f JOIN t_decision_point_rule_set prs "
                + "ON prs.point_version_id=f.point_version_id JOIN t_rule_set_version rsv "
                + "ON rsv.rule_set_version_id=prs.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_decision_point_rule_set prs JOIN t_rule_set_version rsv "
                + "ON rsv.rule_set_version_id=prs.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_risk_rule r JOIN t_rule_set_version rsv "
                + "ON rsv.rule_set_version_id=r.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_rule_node n JOIN t_risk_rule r ON r.rule_id=n.rule_id "
                + "JOIN t_rule_set_version rsv ON rsv.rule_set_version_id=r.rule_set_version_id "
                + "WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_rule_operand o JOIN t_rule_node n ON n.node_id=o.node_id "
                + "JOIN t_risk_rule r ON r.rule_id=n.rule_id JOIN t_rule_set_version rsv "
                + "ON rsv.rule_set_version_id=r.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode),
            count("SELECT COUNT(*) FROM t_rule_action a JOIN t_risk_rule r ON r.rule_id=a.rule_id "
                + "JOIN t_rule_set_version rsv ON rsv.rule_set_version_id=r.rule_set_version_id "
                + "WHERE rsv.rule_set_code=?", ruleSetCode));
    }

    /**
     * 精确删除一个测试 Rule Set 及其派生 Candidate、Audit 和命令幂等行。
     */
    public void deleteOwnedDecisionConfiguration(String ruleSetCode, Collection<String> idempotencyKeys) {
        requireOwnedRuleSetCode(ruleSetCode);
        List<Long> ruleSetVersionIds = ids(
            "SELECT rule_set_version_id FROM t_rule_set_version WHERE rule_set_code=?", ruleSetCode);
        List<Long> pointVersionIds = idsFor("SELECT point_version_id FROM t_decision_point_rule_set "
            + "WHERE rule_set_version_id IN (%s)", ruleSetVersionIds);
        List<Long> ruleIds = idsFor("SELECT rule_id FROM t_risk_rule WHERE rule_set_version_id IN (%s)",
            ruleSetVersionIds);
        List<Long> nodeIds = idsFor("SELECT node_id FROM t_rule_node WHERE rule_id IN (%s)", ruleIds);
        List<Long> ruleActionIds = idsFor("SELECT rule_action_id FROM t_rule_action WHERE rule_id IN (%s)", ruleIds);
        List<Long> releaseIds = idsFor("SELECT release_id FROM t_decision_release WHERE point_version_id IN (%s)",
            pointVersionIds);

        deleteIds("t_release_audit_event", "release_id", releaseIds);
        deleteIds("t_decision_release", "release_id", releaseIds);
        deleteIds("t_rule_action_parameter", "rule_action_id", ruleActionIds);
        deleteIds("t_rule_action", "rule_action_id", ruleActionIds);
        deleteIds("t_rule_operand", "node_id", nodeIds);
        deleteIds("t_rule_node", "node_id", nodeIds);
        deleteIds("t_risk_rule", "rule_id", ruleIds);
        deleteIds("t_decision_point_obligation", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_feature", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_rule_set", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_input_field", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_version", "point_version_id", pointVersionIds);
        deleteIds("t_rule_set", "current_rule_set_version_id", ruleSetVersionIds);
        deleteIds("t_rule_set_version", "rule_set_version_id", ruleSetVersionIds);
        for (String idempotencyKey : idempotencyKeys) {
            requireOwnedKey(idempotencyKey);
            jdbc.update("DELETE FROM t_command_idempotency WHERE idempotency_key=?", idempotencyKey);
        }
    }

    /**
     * 按测试拥有的 Rule Set Code 和准确命令三元组清理，并在返回前证明零残留。
     */
    public void deleteOwnedDecisionConfigurations(
        Collection<String> ruleSetCodes,
        Collection<OwnedCommandIdentity> commands
    ) {
        for (String ruleSetCode : ruleSetCodes) {
            requireOwnedRuleSetCode(ruleSetCode);
        }
        List<Long> ruleSetVersionIds = ruleSetCodes.stream()
            .flatMap(code -> ids("SELECT rule_set_version_id FROM t_rule_set_version WHERE rule_set_code=?", code)
                .stream())
            .toList();
        List<Long> pointVersionIds = idsFor("SELECT point_version_id FROM t_decision_point_rule_set "
            + "WHERE rule_set_version_id IN (%s)", ruleSetVersionIds);
        List<Long> ruleIds = idsFor("SELECT rule_id FROM t_risk_rule WHERE rule_set_version_id IN (%s)",
            ruleSetVersionIds);
        List<Long> nodeIds = idsFor("SELECT node_id FROM t_rule_node WHERE rule_id IN (%s)", ruleIds);
        List<Long> ruleActionIds = idsFor("SELECT rule_action_id FROM t_rule_action WHERE rule_id IN (%s)", ruleIds);
        List<Long> releaseIds = idsFor("SELECT release_id FROM t_decision_release WHERE point_version_id IN (%s)",
            pointVersionIds);
        List<Long> obligationIds = idsFor("SELECT obligation_id FROM t_decision_point_obligation "
            + "WHERE point_version_id IN (%s)", pointVersionIds);

        deleteIds("t_release_audit_event", "release_id", releaseIds);
        deleteIds("t_decision_release", "release_id", releaseIds);
        deleteIds("t_rule_action_parameter", "rule_action_id", ruleActionIds);
        deleteIds("t_rule_action", "rule_action_id", ruleActionIds);
        deleteIds("t_rule_operand", "node_id", nodeIds);
        deleteIds("t_rule_node", "node_id", nodeIds);
        deleteIds("t_risk_rule", "rule_id", ruleIds);
        deleteIds("t_decision_point_limit_binding", "obligation_id", obligationIds);
        deleteIds("t_decision_point_obligation", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_feature", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_rule_set", "point_version_id", pointVersionIds);
        deleteIds("t_rule_set", "current_rule_set_version_id", ruleSetVersionIds);
        deleteIds("t_rule_set_version", "rule_set_version_id", ruleSetVersionIds);
        deleteIds("t_decision_point_input_field", "point_version_id", pointVersionIds);
        deleteIds("t_decision_point_version", "point_version_id", pointVersionIds);
        for (OwnedCommandIdentity command : commands) {
            requireOwnedCommand(command);
            jdbc.update("DELETE FROM t_command_idempotency WHERE caller_scope=? AND operation_type=? "
                    + "AND BINARY idempotency_key=BINARY ?",
                command.callerScope(), command.operationType(), command.idempotencyKey());
        }

        for (String ruleSetCode : ruleSetCodes) {
            if (snapshotCounts(ruleSetCode).total() != 0) {
                throw new IllegalStateException("Risk Decision 自动测试清理后仍有快照残留");
            }
        }
        for (OwnedCommandIdentity command : commands) {
            if (ownedCommandBinaryCount(command.callerScope(), command.operationType(), command.idempotencyKey())
                != 0) {
                throw new IllegalStateException("Risk Decision 自动测试清理后仍有命令残留");
            }
        }
        requireNoIds("t_release_audit_event", "release_id", releaseIds);
        requireNoIds("t_decision_release", "release_id", releaseIds);
        requireNoIds("t_rule_action_parameter", "rule_action_id", ruleActionIds);
        requireNoIds("t_rule_action", "rule_action_id", ruleActionIds);
        requireNoIds("t_rule_operand", "node_id", nodeIds);
        requireNoIds("t_rule_node", "node_id", nodeIds);
        requireNoIds("t_risk_rule", "rule_id", ruleIds);
        requireNoIds("t_decision_point_limit_binding", "obligation_id", obligationIds);
        requireNoIds("t_decision_point_obligation", "point_version_id", pointVersionIds);
        requireNoIds("t_decision_point_feature", "point_version_id", pointVersionIds);
        requireNoIds("t_decision_point_rule_set", "point_version_id", pointVersionIds);
        requireNoIds("t_rule_set", "current_rule_set_version_id", ruleSetVersionIds);
        requireNoIds("t_rule_set_version", "rule_set_version_id", ruleSetVersionIds);
        requireNoIds("t_decision_point_input_field", "point_version_id", pointVersionIds);
        requireNoIds("t_decision_point_version", "point_version_id", pointVersionIds);
    }

    public long ruleSetVersionCount(String ruleSetCode) {
        return count("SELECT COUNT(*) FROM t_rule_set_version WHERE rule_set_code=?", ruleSetCode);
    }

    public long releaseCount(String ruleSetCode) {
        requireOwnedRuleSetCode(ruleSetCode);
        return count("SELECT COUNT(*) FROM t_decision_release r JOIN t_decision_point_rule_set prs "
            + "ON prs.point_version_id=r.point_version_id JOIN t_rule_set_version rsv "
            + "ON rsv.rule_set_version_id=prs.rule_set_version_id WHERE rsv.rule_set_code=?", ruleSetCode);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private List<Long> ids(String sql, Object... arguments) {
        return jdbc.queryForList(sql, Long.class, arguments);
    }

    private List<Long> idsFor(String sqlTemplate, List<Long> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        return ids(sqlTemplate.formatted(placeholders(values.size())), values.toArray());
    }

    private void deleteIds(String table, String column, List<Long> values) {
        if (values.isEmpty()) {
            return;
        }
        List<Object> arguments = new ArrayList<>(values);
        jdbc.update("DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders(values.size()) + ")",
            arguments.toArray());
    }

    private void requireNoIds(String table, String column, List<Long> values) {
        if (values.isEmpty()) {
            return;
        }
        long remaining = count("SELECT COUNT(*) FROM " + table + " WHERE " + column + " IN ("
            + placeholders(values.size()) + ")", values.toArray());
        if (remaining != 0) {
            throw new IllegalStateException("Risk Decision 自动测试清理后仍有子表残留: " + table);
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private void requireOwnedKey(String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("risk-auto-[A-Za-z0-9]{16,96}")) {
            throw new IllegalArgumentException("只允许操作当前 Risk auto 测试拥有的幂等键");
        }
    }

    private void requireOwnedCommand(OwnedCommandIdentity command) {
        if (command == null || command.callerScope() == null || command.operationType() == null) {
            throw new IllegalArgumentException("命令清理必须提供准确三元业务键");
        }
        requireOwnedKey(command.idempotencyKey());
    }

    private void requireOwnedRuleSetCode(String ruleSetCode) {
        if (ruleSetCode == null || !ruleSetCode.matches("AUTO_[A-F0-9]{32}")) {
            throw new IllegalArgumentException("只允许操作当前 Risk auto 测试拥有的 Rule Set");
        }
    }

    public record DecisionSnapshotCounts(
        long ruleSetVersions,
        long pointVersions,
        long inputFields,
        long pointRuleSetBindings,
        long rules,
        long nodes,
        long operands,
        long actions
    ) {
        public long total() {
            return ruleSetVersions + pointVersions + inputFields + pointRuleSetBindings + rules + nodes + operands
                + actions;
        }
    }

    public record OwnedCommandIdentity(String callerScope, String operationType, String idempotencyKey) {
    }

    public static final class DecisionPointLock implements AutoCloseable {

        private final Connection connection;

        private DecisionPointLock(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            try {
                connection.rollback();
            } catch (Exception failure) {
                throw new IllegalStateException("释放 Decision Point 测试锁失败", failure);
            } finally {
                try {
                    connection.close();
                } catch (Exception failure) {
                    throw new IllegalStateException("关闭 Decision Point 测试连接失败", failure);
                }
            }
        }
    }
}
