package com.paytech.compliance.test.mapper;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 审核案件相关表的只读测试 SQL 与有界等待
 * <li>行以列名到值的 Map 返回，供 Checker 与用例断言落库事实；写入只有 {@link #expireDueTime(long)} 一处（模拟补件期限已过）。</li>
 */
@TestComponent
public class ReviewTestMapper {
    /** 异步执行的最长等待。 */
    private static final long WAIT_MILLIS = 20_000;

    private final JdbcTemplate jdbc;

    /** 注入测试库 JDBC。 */
    public ReviewTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 案件行。 */
    public Map<String, Object> caseRow(long caseId) {
        return jdbc.queryForMap("SELECT * FROM t_due_diligence_case WHERE case_id=?", caseId);
    }

    /** 某申请的全部案件，按轮次与案件 ID 升序。 */
    public List<Map<String, Object>> casesOfApplication(long applicationId) {
        return jdbc.queryForList("SELECT * FROM t_due_diligence_case WHERE source_application_id=? ORDER BY round_no,case_id", applicationId);
    }

    /** 主案件的子案件。 */
    public List<Map<String, Object>> childCases(long parentCaseId) {
        return jdbc.queryForList("SELECT c.* FROM t_due_diligence_case c JOIN t_dd_case_dependency d ON d.child_case_id=c.case_id "
            + "WHERE d.parent_case_id=? GROUP BY c.case_id ORDER BY c.case_id", parentCaseId);
    }

    /** 依赖行。 */
    public List<Map<String, Object>> dependencies(long parentCaseId) {
        return jdbc.queryForList("SELECT * FROM t_dd_case_dependency WHERE parent_case_id=? ORDER BY dependency_type", parentCaseId);
    }

    /** 案件主体。 */
    public Map<String, Object> subject(long caseId) {
        return jdbc.queryForMap("SELECT * FROM t_dd_case_subject WHERE case_id=?", caseId);
    }

    /** 案件材料要求（含要求代码）。 */
    public List<Map<String, Object>> requirements(long caseId) {
        return jdbc.queryForList("SELECT r.*,p.requirement_code FROM t_dd_requirement r JOIN t_dd_program_requirement p "
            + "ON p.program_requirement_id=r.program_requirement_id WHERE r.case_id=? ORDER BY p.requirement_code", caseId);
    }

    /** 案件证据（含要求代码）。 */
    public List<Map<String, Object>> evidence(long caseId) {
        return jdbc.queryForList("SELECT e.*,p.requirement_code FROM t_dd_evidence_ref e JOIN t_dd_requirement r ON r.requirement_id="
            + "e.requirement_id JOIN t_dd_program_requirement p ON p.program_requirement_id=r.program_requirement_id WHERE r.case_id=? "
            + "ORDER BY e.evidence_id", caseId);
    }

    /** 案件检查（含检查代码）。 */
    public List<Map<String, Object>> checks(long caseId) {
        return jdbc.queryForList("SELECT c.*,p.check_code FROM t_dd_check c JOIN t_dd_program_check p ON p.program_check_id="
            + "c.program_check_id WHERE c.case_id=? ORDER BY p.check_code", caseId);
    }

    /** 案件筛查执行（含筛查类型）。 */
    public List<Map<String, Object>> runs(long caseId) {
        return jdbc.queryForList("SELECT r.*,p.screening_type FROM t_screening_run r JOIN t_dd_program_screening p ON "
            + "p.program_screening_id=r.program_screening_id WHERE r.case_id=? ORDER BY p.screening_type,r.started_time", caseId);
    }

    /** 案件命中。 */
    public List<Map<String, Object>> hits(long caseId) {
        return jdbc.queryForList("SELECT h.* FROM t_screening_hit h JOIN t_screening_run r ON r.screening_run_id=h.screening_run_id "
            + "WHERE r.case_id=? ORDER BY h.hit_id", caseId);
    }

    /** 命中名单类型。 */
    public List<String> hitLists(long hitId) {
        return jdbc.queryForList("SELECT list_type FROM t_screening_hit_list WHERE hit_id=? ORDER BY list_type", String.class, hitId);
    }

    /** 命中处置。 */
    public List<Map<String, Object>> resolutions(long hitId) {
        return jdbc.queryForList("SELECT * FROM t_screening_resolution WHERE hit_id=? ORDER BY resolution_version", hitId);
    }

    /** 风险因子。 */
    public List<Map<String, Object>> riskFactors(long caseId) {
        return jdbc.queryForList("SELECT * FROM t_dd_risk_factor WHERE case_id=? ORDER BY factor_code,factor_value", caseId);
    }

    /** 退回错误。 */
    public List<Map<String, Object>> returnErrors(long caseId) {
        return jdbc.queryForList("SELECT * FROM t_dd_return_error WHERE case_id=? ORDER BY sequence_no", caseId);
    }

    /** 状态变更。 */
    public List<Map<String, Object>> stateChanges(long caseId) {
        return jdbc.queryForList("SELECT * FROM t_dd_case_state_change WHERE case_id=? ORDER BY aggregate_version", caseId);
    }

    /** 案件的资格决定。 */
    public List<Map<String, Object>> decisions(long caseId) {
        return jdbc.queryForList("SELECT * FROM t_qualification_decision WHERE case_id=? ORDER BY decision_id", caseId);
    }

    /** 角色资格；不存在返回 null。 */
    public Map<String, Object> qualification(String roleType, String roleId, String qualificationType) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM t_role_qualification WHERE role_type=? AND role_id=? AND "
            + "qualification_type=?", roleType, roleId, qualificationType);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 某类型、某事件键的补偿任务。 */
    public List<Map<String, Object>> events(String eventType, String eventKey) {
        return jdbc.queryForList("SELECT * FROM t_compensation_event WHERE event_type=? AND event_key=?", eventType, eventKey);
    }

    /** 把 RETURNED 案件的补件期限改到过去（模拟时间流逝）。 */
    public void expireDueTime(long caseId) {
        jdbc.update("UPDATE t_due_diligence_case SET review_due_time=UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE case_id=?", caseId);
    }

    /** 等待该主案件的筛查补偿任务全部结束（成功后删除；失败停留为 ERROR/FAILED 或 INIT 待重试）。 */
    public void awaitScreeningSettled(long caseId) {
        await(() -> events("CASE_SCREENING", Long.toString(caseId)).stream()
            .noneMatch(row -> "PROCESSING".equals(row.get("execute_status")) || isDue(row)), "筛查任务未结束: " + caseId);
    }

    /** 等待该主案件的筛查任务全部成功删除。 */
    public void awaitScreeningCompleted(long caseId) {
        await(() -> events("CASE_SCREENING", Long.toString(caseId)).isEmpty(), "筛查任务未完成: " + caseId);
    }

    /** 尚未执行且已到执行时间的任务。 */
    boolean isDue(Map<String, Object> row) {
        return "INIT".equals(row.get("execute_status")) && ((Number)row.get("execute_count")).intValue() == 0;
    }

    /** 有界条件等待。 */
    public static void await(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail(message);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待被中断: " + message);
            }
        }
    }
}
