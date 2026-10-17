package com.paytech.risk.test.mapper.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 结账风控评估、执行发布与结账决策点测试配置的数据读取与清理
 * <li>评估族（评估、Feature 结果、规则与流程轨迹）、结账输入快照、结账执行发布及其审计与影子批次按结账决策点整体清理；
 * 支付与结账共用的影子批次表只删除候选为结账发布的行。</li>
 * <li>流程、规则集、变量（含 Feature 定义与版本）与名单只清理编码以 {@link #TEST_CODE_PREFIX} 开头的测试数据，初始化变量 91001～91020 保持不变；累计事件按结账来源类型清理。</li>
 *
 * @author Kenny
 */
@TestComponent
public class CheckoutRiskTestMapper {
    /** 结账决策点 ID（初始化数据）。 */
    public static final long CHECKOUT_POINT_ID = 210001L;

    /** 结账运行期冻结点版本 ID（初始化数据）。 */
    public static final long RUNTIME_POINT_VERSION_ID = 220001L;

    /** 结账测试创建的流程、规则集与变量编码前缀。 */
    public static final String TEST_CODE_PREFIX = "T_CHK";

    private static final String LIKE_TEST_CODE = "LIKE 'T\\_CHK%'";

    private static final String CHECKOUT_EVALUATIONS =
        "SELECT evaluation_id FROM t_decision_evaluation WHERE decision_point_id=" + CHECKOUT_POINT_ID;

    private static final String CHECKOUT_EXECUTIONS = "SELECT release_id FROM t_decision_release WHERE decision_point_id="
        + CHECKOUT_POINT_ID + " AND decision_flow_version_id IS NOT NULL";

    private static final String TEST_RULE_IDS = "SELECT r.rule_id FROM t_risk_rule r JOIN t_rule_set_version v "
        + "ON v.rule_set_version_id=r.rule_set_version_id WHERE v.decision_point_id=" + CHECKOUT_POINT_ID
        + " AND v.rule_set_code " + LIKE_TEST_CODE;

    private static final String TEST_FLOW_IDS = "SELECT flow_id FROM t_decision_flow WHERE decision_point_id="
        + CHECKOUT_POINT_ID + " AND flow_code " + LIKE_TEST_CODE;

    private static final String TEST_FLOW_VERSION_IDS =
        "SELECT flow_version_id FROM t_decision_flow_version WHERE flow_id IN (" + TEST_FLOW_IDS + ")";

    /** 结账累计事件来源类型。 */
    public static final String SOURCE_TYPE = "CHECKOUT_INPUT_SNAPSHOT";

    private static final String CHECKOUT_EVENTS =
        "SELECT velocity_event_id FROM t_velocity_event WHERE source_type='" + SOURCE_TYPE + "'";

    private static final String TEST_FEATURES = "SELECT feature_version_id FROM t_risk_variable WHERE "
        + "feature_version_id IS NOT NULL AND point_code='CHECKOUT_PREPARE' AND variable_code " + LIKE_TEST_CODE;

    private static final String TEST_LISTS = "SELECT list_id FROM t_risk_list WHERE list_code " + LIKE_TEST_CODE;

    private final JdbcTemplate jdbc;

    public CheckoutRiskTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入一条结账累计事件及其主体，供窗口边界用例构造历史；来源 ID 取事件 ID。 */
    public void insertVelocityEvent(long eventId, String eventCode, Instant occurred, BigDecimal amount, String currency,
        Map<String, String> subjects, long subjectIdBase) {
        jdbc.update("INSERT INTO t_velocity_event(velocity_event_id, event_code, source_type, source_id, source_version, "
            + "occurred_time, amount, currency, event_hash, create_time) VALUES(?,?,?,?,?,?,?,?,?,?)", eventId, eventCode,
            SOURCE_TYPE, eventId, 1, utc(occurred), amount, currency, "0".repeat(64), utc(occurred));
        long id = subjectIdBase;
        for (Map.Entry<String, String> subject : subjects.entrySet()) {
            jdbc.update("INSERT INTO t_velocity_event_subject(event_subject_id, velocity_event_id, subject_type, subject_key, "
                + "event_code, occurred_time, create_time) VALUES(?,?,?,?,?,?,?)", id++, eventId, subject.getKey(),
                subject.getValue(), eventCode, utc(occurred), utc(occurred));
        }
    }

    /** 按来源读取累计事件，按事件编码（评估事件在前）。 */
    public List<Map<String, Object>> velocityEvents(String sourceType, long sourceId) {
        return jdbc.queryForList("SELECT * FROM t_velocity_event WHERE source_type=? AND source_id=? ORDER BY event_code",
            sourceType, sourceId);
    }

    /** 读取一条累计事件的主体，按主体类型。 */
    public List<Map<String, Object>> velocitySubjects(long velocityEventId) {
        return jdbc.queryForList("SELECT * FROM t_velocity_event_subject WHERE velocity_event_id=? ORDER BY subject_type",
            velocityEventId);
    }

    /** 统计某来源类型的累计事件。 */
    public int countVelocityEvents(String sourceType) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_velocity_event WHERE source_type=?", Integer.class,
            sourceType);
        return count == null ? 0 : count;
    }

    /** 读取一次评估的 Feature 结果，按执行顺序。 */
    public List<Map<String, Object>> featureResults(long evaluationId) {
        return jdbc.queryForList("SELECT * FROM t_evaluation_feature_result WHERE evaluation_id=? ORDER BY execution_sequence",
            evaluationId);
    }

    /** 读取累计变量关联的 Feature Version 与参数。 */
    public Map<String, Object> velocityFeature(String variableCode) {
        return jdbc.queryForMap("SELECT v.feature_version_id, d.event_code, d.anchor_subject_type, d.metric, d.window_seconds, "
            + "d.include_current FROM t_risk_variable v JOIN t_velocity_feature_definition d "
            + "ON d.feature_version_id=v.feature_version_id WHERE v.variable_code=?", variableCode);
    }

    /** 读取名单命中变量关联的 Feature Version 与名单主体。 */
    public Map<String, Object> listFeature(String variableCode) {
        return jdbc.queryForMap("SELECT v.feature_version_id, d.list_id, d.subject_type FROM t_risk_variable v "
            + "JOIN t_list_feature_definition d ON d.feature_version_id=v.feature_version_id WHERE v.variable_code=?",
            variableCode);
    }

    /** 读取订单的结账输入快照；不存在为空。 */
    public Map<String, Object> snapshot(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_checkout_input_snapshot WHERE order_id=?",
            orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 统计结账输入快照。 */
    public int countSnapshots() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_checkout_input_snapshot", Integer.class);
        return count == null ? 0 : count;
    }

    /** 读取评估。 */
    public Map<String, Object> evaluation(long evaluationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_decision_evaluation WHERE evaluation_id=?",
            evaluationId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 统计结账决策点的正式评估。 */
    public int countPrimaryEvaluations() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_decision_evaluation WHERE decision_point_id=? "
            + "AND evaluation_mode='PRIMARY'", Integer.class, CHECKOUT_POINT_ID);
        return count == null ? 0 : count;
    }

    /** 读取一次评估的规则或谓词轨迹，按执行顺序。 */
    public List<Map<String, Object>> traces(long evaluationId, String traceType) {
        return jdbc.queryForList("SELECT * FROM t_evaluation_rule_trace WHERE evaluation_id=? AND trace_type=? "
            + "ORDER BY execution_sequence", evaluationId, traceType);
    }

    /** 读取一次评估经过的流程节点键，按经过顺序。 */
    public List<String> flowNodeKeys(long evaluationId) {
        return jdbc.queryForList("SELECT node_key FROM t_evaluation_flow_trace WHERE evaluation_id=? ORDER BY step_no",
            String.class, evaluationId);
    }

    /** 统计一次评估的 Feature 结果。 */
    public int countFeatureResults(long evaluationId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_evaluation_feature_result WHERE evaluation_id=?",
            Integer.class, evaluationId);
        return count == null ? 0 : count;
    }

    /** 规则集版本所属管理根的 ID、编码与管理版本。 */
    public Map<String, Object> ruleSetRoot(long ruleSetVersionId) {
        return jdbc.queryForMap("SELECT rs.rule_set_id, rs.rule_set_code, rs.version FROM t_rule_set rs JOIN t_rule_set_version v "
            + "ON v.decision_point_id=rs.decision_point_id AND v.rule_set_code=rs.rule_set_code "
            + "WHERE v.rule_set_version_id=?", ruleSetVersionId);
    }

    /** 读取发布行。 */
    public Map<String, Object> release(long releaseId) {
        return jdbc.queryForMap("SELECT * FROM t_decision_release WHERE release_id=?", releaseId);
    }

    /** 读取影子批次的样本评估。 */
    public List<Map<String, Object>> shadowSamples(long shadowRunId) {
        return jdbc.queryForList("SELECT * FROM t_decision_evaluation WHERE shadow_run_id=? ORDER BY evaluation_id",
            shadowRunId);
    }

    /** 结账运行期点版本的超时毫秒数。 */
    public int pointTimeoutMillis() {
        Integer value = jdbc.queryForObject("SELECT timeout_ms FROM t_decision_point_version WHERE point_version_id=?",
            Integer.class, RUNTIME_POINT_VERSION_ID);
        return value == null ? 0 : value;
    }

    /** 改写结账运行期点版本的超时毫秒数（只改 220001，不触及旧 companion 版本）；由 Mocker 清理时恢复。 */
    public void updatePointTimeoutMillis(int timeoutMillis) {
        jdbc.update("UPDATE t_decision_point_version SET timeout_ms=? WHERE point_version_id=?", timeoutMillis,
            RUNTIME_POINT_VERSION_ID);
    }

    /** 把已发布规则引用的变量标为删除，构造运行期引用损坏。 */
    public void markVariableDeleted(String variableCode) {
        jdbc.update("UPDATE t_risk_variable SET status='DELETED' WHERE variable_code=?", variableCode);
    }

    /** 清理结账评估运行期数据、结账执行发布与测试创建的结账配置；初始化目录保持不变。 */
    public void deleteAll() {
        jdbc.update("DELETE FROM t_evaluation_feature_result WHERE evaluation_id IN (" + CHECKOUT_EVALUATIONS + ")");
        jdbc.update("DELETE FROM t_evaluation_rule_trace WHERE evaluation_id IN (" + CHECKOUT_EVALUATIONS + ")");
        jdbc.update("DELETE FROM t_evaluation_flow_trace WHERE evaluation_id IN (" + CHECKOUT_EVALUATIONS + ")");
        jdbc.update("DELETE FROM t_checkout_input_snapshot");
        jdbc.update("DELETE FROM t_decision_evaluation WHERE decision_point_id=?", CHECKOUT_POINT_ID);
        deleteExecutions();
        deleteFlows();
        deleteRuleSets();
        jdbc.update("DELETE FROM t_velocity_event_subject WHERE velocity_event_id IN (" + CHECKOUT_EVENTS + ")");
        jdbc.update("DELETE FROM t_velocity_event WHERE source_type=?", SOURCE_TYPE);
        jdbc.update("DELETE FROM t_velocity_feature_definition WHERE feature_version_id IN (" + TEST_FEATURES + ")");
        jdbc.update("DELETE FROM t_list_feature_definition WHERE feature_version_id IN (" + TEST_FEATURES + ")");
        jdbc.update("DELETE FROM t_feature_version WHERE feature_version_id IN (" + TEST_FEATURES + ")");
        jdbc.update("DELETE FROM t_risk_list_entry WHERE list_id IN (" + TEST_LISTS + ")");
        jdbc.update("DELETE FROM t_risk_list WHERE list_code " + LIKE_TEST_CODE);
        jdbc.update("DELETE FROM t_risk_variable_member WHERE variable_code " + LIKE_TEST_CODE);
        jdbc.update("DELETE FROM t_risk_variable WHERE point_code='CHECKOUT_PREPARE' AND variable_code " + LIKE_TEST_CODE);
    }

    /** 结账执行发布的影子批次、补偿任务、状态审计与发布行。 */
    private void deleteExecutions() {
        String shadowRuns = "SELECT shadow_run_id FROM t_release_shadow_run WHERE candidate_release_id IN ("
            + CHECKOUT_EXECUTIONS + ")";
        jdbc.update("DELETE FROM t_compensation_event WHERE event_type='DECISION_SHADOW_RUN' AND event_key IN ("
            + "SELECT CAST(shadow_run_id AS CHAR) FROM (" + shadowRuns + ") runs)");
        jdbc.update("DELETE FROM t_release_shadow_run WHERE candidate_release_id IN (SELECT release_id FROM ("
            + CHECKOUT_EXECUTIONS + ") releases)");
        jdbc.update("DELETE FROM t_release_audit_event WHERE release_id IN (" + CHECKOUT_EXECUTIONS + ")");
        jdbc.update("DELETE FROM t_decision_release WHERE decision_point_id=? AND decision_flow_version_id IS NOT NULL",
            CHECKOUT_POINT_ID);
    }

    /** 测试创建的结账流程及其版本、节点、连线与结束输出。 */
    private void deleteFlows() {
        jdbc.update("DELETE FROM t_decision_flow_end_output WHERE flow_version_id IN (" + TEST_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_edge WHERE flow_version_id IN (" + TEST_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_node WHERE flow_version_id IN (" + TEST_FLOW_VERSION_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow_version WHERE flow_id IN (" + TEST_FLOW_IDS + ")");
        jdbc.update("DELETE FROM t_decision_flow WHERE decision_point_id=? AND flow_code " + LIKE_TEST_CODE,
            CHECKOUT_POINT_ID);
    }

    /** 测试创建的结账规则集及其规则闭包。 */
    private void deleteRuleSets() {
        jdbc.update("DELETE FROM t_rule_action_parameter WHERE rule_action_id IN (SELECT rule_action_id FROM t_rule_action "
            + "WHERE rule_id IN (" + TEST_RULE_IDS + "))");
        jdbc.update("DELETE FROM t_rule_action WHERE rule_id IN (" + TEST_RULE_IDS + ")");
        jdbc.update("DELETE FROM t_rule_operand WHERE node_id IN (SELECT node_id FROM t_rule_node WHERE rule_id IN ("
            + TEST_RULE_IDS + "))");
        jdbc.update("DELETE FROM t_rule_node WHERE rule_id IN (" + TEST_RULE_IDS + ")");
        jdbc.update("DELETE FROM t_risk_rule WHERE rule_set_version_id IN (SELECT rule_set_version_id FROM t_rule_set_version "
            + "WHERE decision_point_id=" + CHECKOUT_POINT_ID + " AND rule_set_code " + LIKE_TEST_CODE + ")");
        jdbc.update("DELETE FROM t_rule_set_version WHERE decision_point_id=? AND rule_set_code " + LIKE_TEST_CODE,
            CHECKOUT_POINT_ID);
        jdbc.update("DELETE FROM t_rule_set WHERE decision_point_id=? AND rule_set_code " + LIKE_TEST_CODE,
            CHECKOUT_POINT_ID);
    }

    /** UTC 时间按数据库 DATETIME(6) 写入。 */
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
