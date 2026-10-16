package com.paytech.risk.test.mapper.evaluation;

import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 结账风控评估、执行发布与结账决策点测试配置的数据读取与清理
 * <li>评估族（评估、Feature 结果、规则与流程轨迹）、结账输入快照、结账执行发布及其审计与影子批次按结账决策点整体清理；
 * 支付与结账共用的影子批次表只删除候选为结账发布的行。</li>
 * <li>流程、规则集与变量只清理编码以 {@link #TEST_CODE_PREFIX} 开头的测试数据，初始化变量 91001～91020 保持不变。</li>
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

    private final JdbcTemplate jdbc;

    public CheckoutRiskTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
