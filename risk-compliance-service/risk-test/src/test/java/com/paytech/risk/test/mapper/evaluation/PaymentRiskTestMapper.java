package com.paytech.risk.test.mapper.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 支付风控评估、执行发布、累计、名单与支付决策点配置的测试数据准备、读取与清理
 * <li>评估、累计、名单、Feature 与影子批次表没有初始化数据，测试结束整表清理；配置只清理支付决策点下非初始化的行。</li>
 *
 * @author Kenny
 */
@TestComponent
public class PaymentRiskTestMapper {
    /** 支付决策点 ID（初始化数据）。 */
    public static final long PAYMENT_POINT_ID = 210002L;

    private static final String PAYMENT_EVALUATIONS =
        "SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id=" + PAYMENT_POINT_ID;

    private static final String PAYMENT_RULE_IDS = "SELECT r.rule_id FROM t_risk_rule r JOIN t_rule_set_version v "
        + "ON v.rule_set_version_id=r.rule_set_version_id WHERE v.decision_point_id=" + PAYMENT_POINT_ID;

    private static final String PAYMENT_FLOW_VERSION_IDS = "SELECT v.flow_version_id FROM t_decision_flow_version v "
        + "JOIN t_decision_flow f ON f.flow_id=v.flow_id WHERE f.decision_point_id=" + PAYMENT_POINT_ID;

    private final JdbcTemplate jdbc;

    public PaymentRiskTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入一条累计事件及其主体，供窗口边界用例构造历史。 */
    public void insertVelocityEvent(long eventId, String eventCode, Instant occurred, BigDecimal amount, String currency,
        Map<String, String> subjects, long subjectIdBase) {
        jdbc.update("INSERT INTO t_velocity_event(velocity_event_id, event_code, source_type, source_id, source_version, "
            + "occurred_time, amount, currency, event_hash, create_time) VALUES(?,?,?,?,?,?,?,?,?,?)", eventId, eventCode,
            "TRANSACTION_RISK_ASSESSMENT", eventId, 1, utc(occurred), amount, currency, "0".repeat(64), utc(occurred));
        long id = subjectIdBase;
        for (Map.Entry<String, String> subject : subjects.entrySet()) {
            jdbc.update("INSERT INTO t_velocity_event_subject(event_subject_id, velocity_event_id, subject_type, subject_key, "
                + "event_code, occurred_time, create_time) VALUES(?,?,?,?,?,?,?)", id++, eventId, subject.getKey(),
                subject.getValue(), eventCode, utc(occurred), utc(occurred));
        }
    }

    /** 规则集版本所属管理根的 ID、编码与管理版本。 */
    public Map<String, Object> ruleSetRoot(long ruleSetVersionId) {
        return jdbc.queryForMap("SELECT rs.rule_set_id, rs.rule_set_code, rs.version FROM t_rule_set rs JOIN t_rule_set_version v "
            + "ON v.decision_point_id=rs.decision_point_id AND v.rule_set_code=rs.rule_set_code "
            + "WHERE v.rule_set_version_id=?", ruleSetVersionId);
    }

    /** 支付决策点冻结版本的超时毫秒数。 */
    public int pointTimeoutMillis() {
        Integer value = jdbc.queryForObject("SELECT timeout_ms FROM t_decision_point_version WHERE decision_point_id=?",
            Integer.class, PAYMENT_POINT_ID);
        return value == null ? 0 : value;
    }

    /** 改写支付决策点冻结版本的超时毫秒数，用于构造评估超时；由 Mocker 清理时恢复原值。 */
    public void updatePointTimeoutMillis(int timeoutMillis) {
        jdbc.update("UPDATE t_decision_point_version SET timeout_ms=? WHERE decision_point_id=?", timeoutMillis,
            PAYMENT_POINT_ID);
    }

    /** 读取支付尝试的交易评估快照。 */
    public Map<String, Object> assessment(long paymentAttemptId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM t_transaction_risk_assessment WHERE payment_attempt_id=?", paymentAttemptId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 正式评估对应的交易评估 ID。 */
    public long assessmentIdOf(long evaluationId) {
        Long id = jdbc.queryForObject("SELECT assessment_id FROM t_transaction_risk_assessment WHERE evaluation_id=?",
            Long.class, evaluationId);
        return id == null ? 0 : id;
    }

    /** 读取评估。 */
    public Map<String, Object> evaluation(long evaluationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_decision_evaluation WHERE evaluation_id=?",
            evaluationId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 读取一次评估的规则或谓词轨迹，按执行顺序。 */
    public List<Map<String, Object>> traces(long evaluationId, String traceType) {
        return jdbc.queryForList("SELECT * FROM t_evaluation_rule_trace WHERE evaluation_id=? AND trace_type=? "
            + "ORDER BY execution_sequence", evaluationId, traceType);
    }

    /** 读取一次评估中各规则的命中结果，按规则编码。 */
    public Map<String, Boolean> ruleHits(long evaluationId) {
        Map<String, Boolean> hits = new TreeMap<>();
        jdbc.query("SELECT r.rule_code, t.result_value FROM t_evaluation_rule_trace t JOIN t_risk_rule r "
            + "ON r.rule_id=t.rule_id WHERE t.evaluation_id=? AND t.trace_type='RULE'",
            rs -> { hits.put(rs.getString(1), rs.getBoolean(2)); }, evaluationId);
        return hits;
    }

    /** 读取一次评估的 Feature 结果。 */
    public List<Map<String, Object>> featureResults(long evaluationId) {
        return jdbc.queryForList("SELECT * FROM t_evaluation_feature_result WHERE evaluation_id=? "
            + "ORDER BY execution_sequence", evaluationId);
    }

    /** 统计交易评估写入的累计事件。 */
    public List<String> velocityEventCodes(long assessmentId) {
        return jdbc.queryForList("SELECT event_code FROM t_velocity_event WHERE source_id=? ORDER BY event_code",
            String.class, assessmentId);
    }

    /** 读取累计事件关联的主体类型与键。 */
    public List<Map<String, Object>> velocitySubjects(long assessmentId, String eventCode) {
        return jdbc.queryForList("SELECT s.subject_type, s.subject_key FROM t_velocity_event_subject s JOIN t_velocity_event e "
            + "ON e.velocity_event_id=s.velocity_event_id WHERE e.source_id=? AND e.event_code=? ORDER BY s.subject_type",
            assessmentId, eventCode);
    }

    /** 统计支付决策点的正式评估。 */
    public int countPrimaryEvaluations() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id=? "
            + "AND evaluation_mode='PRIMARY'", Integer.class, PAYMENT_POINT_ID);
        return count == null ? 0 : count;
    }

    /** 读取发布行。 */
    public Map<String, Object> release(long releaseId) {
        return jdbc.queryForMap("SELECT * FROM t_decision_release WHERE release_id=?", releaseId);
    }

    /** 读取发布的状态审计，按聚合版本。 */
    public List<Map<String, Object>> releaseAudits(long releaseId) {
        return jdbc.queryForList("SELECT * FROM t_release_audit_event WHERE release_id=? ORDER BY event_type, "
            + "aggregate_version, check_code", releaseId);
    }

    /** 读取影子批次的样本评估。 */
    public List<Map<String, Object>> shadowSamples(long shadowRunId) {
        return jdbc.queryForList("SELECT * FROM t_decision_evaluation WHERE shadow_run_id=? ORDER BY evaluation_id",
            shadowRunId);
    }

    /** 读取影子批次补偿任务最近一次可重试失败的错误代码；尚未失败时为空。 */
    public String shadowRunRetryError(long shadowRunId) {
        List<String> codes = jdbc.queryForList("SELECT last_error_code FROM t_compensation_event WHERE event_type="
            + "'DECISION_SHADOW_RUN' AND event_key=? AND execute_status='ERROR'", String.class, Long.toString(shadowRunId));
        return codes.isEmpty() ? null : codes.getFirst();
    }

    /** 读取名单成员行。 */
    public Map<String, Object> listEntry(long entryId) {
        return jdbc.queryForMap("SELECT * FROM t_risk_list_entry WHERE entry_id=?", entryId);
    }

    /** 读取 Feature 变量关联的 Feature Version 与参数。 */
    public Map<String, Object> velocityFeature(String variableCode) {
        return jdbc.queryForMap("SELECT v.feature_version_id, f.handler_code, f.status, d.event_code, d.anchor_subject_type, "
            + "d.metric, d.distinct_subject_type, d.window_seconds, d.include_current FROM t_risk_variable v "
            + "JOIN t_feature_version f ON f.feature_version_id=v.feature_version_id "
            + "JOIN t_velocity_feature_definition d ON d.feature_version_id=v.feature_version_id WHERE v.variable_code=?",
            variableCode);
    }

    /** 读取名单命中变量关联的 Feature Version 与名单参数。 */
    public Map<String, Object> listFeature(String variableCode) {
        return jdbc.queryForMap("SELECT v.feature_version_id, f.handler_code, f.status, d.list_id, d.subject_type "
            + "FROM t_risk_variable v JOIN t_feature_version f ON f.feature_version_id=v.feature_version_id "
            + "JOIN t_list_feature_definition d ON d.feature_version_id=v.feature_version_id WHERE v.variable_code=?",
            variableCode);
    }

    /** 统计 Feature Version。 */
    public int countFeatureVersions() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_feature_version", Integer.class);
        return count == null ? 0 : count;
    }

    /** 把已发布规则引用的变量标为删除，构造运行期引用损坏。 */
    public void markVariableDeleted(String variableCode) {
        jdbc.update("UPDATE t_risk_variable SET status='DELETED' WHERE variable_code=?", variableCode);
    }

    /** 清理支付评估运行期数据与支付决策点下测试创建的配置；初始化目录保持不变。 */
    public void deleteAll() {
        jdbc.update("DELETE FROM t_evaluation_feature_result WHERE evaluation_id IN (" + PAYMENT_EVALUATIONS + ")");
        jdbc.update("DELETE FROM t_evaluation_rule_trace WHERE evaluation_id IN (" + PAYMENT_EVALUATIONS + ")");
        jdbc.update("DELETE FROM t_transaction_risk_assessment");
        jdbc.update("DELETE FROM t_decision_evaluation WHERE decision_point_id=?", PAYMENT_POINT_ID);
        jdbc.update("DELETE FROM t_velocity_event_subject");
        jdbc.update("DELETE FROM t_velocity_event");
        jdbc.update("DELETE FROM t_release_shadow_run");
        jdbc.update("DELETE FROM t_compensation_event WHERE event_type='DECISION_SHADOW_RUN'");
        jdbc.update("DELETE FROM t_release_audit_event WHERE release_id IN (SELECT release_id FROM t_decision_release "
            + "WHERE decision_point_id=?)", PAYMENT_POINT_ID);
        jdbc.update("DELETE FROM t_decision_release WHERE decision_point_id=?", PAYMENT_POINT_ID);
        deleteFlows();
        deleteRuleSets();
        jdbc.update("DELETE FROM t_risk_variable_member WHERE variable_code IN (SELECT variable_code FROM t_risk_variable "
            + "WHERE point_code='PAYMENT_ATTEMPT_RISK' AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013)");
        jdbc.update("DELETE FROM t_risk_variable WHERE point_code='PAYMENT_ATTEMPT_RISK' "
            + "AND variable_id NOT BETWEEN 900000000000093001 AND 900000000000093013");
        jdbc.update("DELETE FROM t_velocity_feature_definition");
        jdbc.update("DELETE FROM t_list_feature_definition");
        jdbc.update("DELETE FROM t_feature_version");
        jdbc.update("DELETE FROM t_risk_list_entry");
        jdbc.update("DELETE FROM t_risk_list");
    }

    /** 支付决策点的流程及其版本、节点、连线。 */
    private void deleteFlows() {
        jdbc.update("DELETE FROM t_decision_flow_end_output WHERE flow_version_id IN (" + PAYMENT_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (" + PAYMENT_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_node WHERE flow_version_id IN (" + PAYMENT_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_id IN (SELECT flow_id FROM t_decision_flow "
            + "WHERE decision_point_id=?)", PAYMENT_POINT_ID);
        jdbc.update("DELETE FROM t_decision_flow WHERE decision_point_id=?", PAYMENT_POINT_ID);
    }

    /** 支付决策点的规则集及其规则闭包。 */
    private void deleteRuleSets() {
        jdbc.update("DELETE FROM t_rule_action_parameter WHERE rule_action_id IN (SELECT rule_action_id FROM t_rule_action "
            + "WHERE rule_id IN (" + PAYMENT_RULE_IDS + "))");
        jdbc.update("DELETE FROM t_rule_action WHERE rule_id IN (" + PAYMENT_RULE_IDS + ")");
        jdbc.update("DELETE FROM t_rule_operand WHERE node_id IN (SELECT node_id FROM t_rule_node WHERE rule_id IN ("
            + PAYMENT_RULE_IDS + "))");
        jdbc.update("DELETE FROM t_rule_node WHERE rule_id IN (" + PAYMENT_RULE_IDS + ")");
        jdbc.update("DELETE FROM t_risk_rule WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM t_rule_set_version "
            + "WHERE decision_point_id=?)", PAYMENT_POINT_ID);
        jdbc.update("DELETE FROM t_rule_set_version WHERE decision_point_id=?", PAYMENT_POINT_ID);
        jdbc.update("DELETE FROM t_rule_set WHERE decision_point_id=?", PAYMENT_POINT_ID);
    }

    /** UTC 墙钟时间参数；驱动不按 JVM 时区换算 LocalDateTime。 */
    private static LocalDateTime utc(Instant time) {
        return LocalDateTime.ofInstant(time, ZoneOffset.UTC);
    }
}
