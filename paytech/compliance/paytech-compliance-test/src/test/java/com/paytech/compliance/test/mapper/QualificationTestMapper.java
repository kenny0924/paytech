package com.paytech.compliance.test.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 角色资格三张表（资格决定、角色资格投影、资格状态变更）与结算控制根表的测试 SQL
 * <li>写入只供本地 Mocker 准备前置事实；读取供 Checker 核对落库结果，时间一律按 UTC 读写。</li>
 * <li>本测试库为专用环境，用例结束由 ComplianceDataCleaner 统一清理全部表。</li>
 *
 * @author Kenny
 */
@TestComponent
public class QualificationTestMapper {
    private final JdbcTemplate jdbc;

    public QualificationTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 角色资格投影行。 */
    public record QualificationRow(long qualificationId, String roleType, String roleId, String qualificationType,
        long currentDecisionId, String status, Instant validFrom, Instant validTo, String reviewFrequencyCode,
        Instant nextReviewTime, String reviewStatus, long version, Instant updateTime) {}

    /** 资格决定行。 */
    public record DecisionRow(long decisionId, Long caseId, Integer decisionVersion, String roleType, String roleId,
        String qualificationType, String decisionSource, String outcome, String overrideReasonCode, String basisNote,
        Instant validFrom, Instant validTo, Long decidedBy, Instant decidedTime) {}

    /** 资格状态变更行。 */
    public record StateChangeRow(long changeId, long qualificationId, String fromStatus, String toStatus, long decisionId,
        String reasonCode, String triggerType, String triggerId, Long actorId, long aggregateVersion, Instant occurredTime) {}

    /** 插入角色资格投影。 */
    public void insertQualification(QualificationRow row) {
        jdbc.update("INSERT INTO t_role_qualification(qualification_id,role_type,role_id,qualification_type,current_decision_id,"
            + "status,valid_from,valid_to,review_frequency_code,next_review_time,review_status,version,create_time,update_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", row.qualificationId(), row.roleType(), row.roleId(), row.qualificationType(),
            row.currentDecisionId(), row.status(), utc(row.validFrom()), utc(row.validTo()), row.reviewFrequencyCode(),
            utc(row.nextReviewTime()), row.reviewStatus(), row.version(), utc(row.updateTime()), utc(row.updateTime()));
    }

    /** 插入资格决定。 */
    public void insertDecision(DecisionRow row) {
        jdbc.update("INSERT INTO t_qualification_decision(decision_id,case_id,decision_version,role_type,role_id,"
            + "qualification_type,decision_source,outcome,override_reason_code,basis_note,valid_from,valid_to,decided_by,"
            + "decided_time,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", row.decisionId(), row.caseId(),
            row.decisionVersion(), row.roleType(), row.roleId(), row.qualificationType(), row.decisionSource(), row.outcome(),
            row.overrideReasonCode(), row.basisNote(), utc(row.validFrom()), utc(row.validTo()), row.decidedBy(),
            utc(row.decidedTime()));
    }

    /** 插入资格状态变更。 */
    public void insertStateChange(StateChangeRow row) {
        jdbc.update("INSERT INTO t_qualification_state_change(change_id,qualification_id,from_status,to_status,decision_id,"
            + "reason_code,trigger_type,trigger_id,actor_id,aggregate_version,occurred_time,create_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", row.changeId(), row.qualificationId(), row.fromStatus(),
            row.toStatus(), row.decisionId(), row.reasonCode(), row.triggerType(), row.triggerId(), row.actorId(),
            row.aggregateVersion(), utc(row.occurredTime()));
    }

    /** 插入一条结算控制根行；作用域与状态历史不参与资格判定，不插入。 */
    public void insertSettlementControl(long controlId, String roleType, long roleId, String controlType, String status,
        Instant effectiveFrom, Instant effectiveTo) {
        jdbc.update("INSERT INTO t_settlement_control(control_id,control_no,role_type,role_id,control_type,reason_code,"
            + "source_action_effect_id,status,effective_from,effective_to,control_instance_key,content_hash,active_slot,version,"
            + "create_time,update_time) VALUES(?,?,?,?,?,'TEST_CONTROL',?,?,?,?,?,?,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            controlId, "SC" + controlId, roleType, roleId, controlType, controlId, status, utc(effectiveFrom), utc(effectiveTo),
            "test:" + controlId, "0".repeat(64), "ACTIVE".equals(status) ? 1 : null);
    }

    /** 某角色的全部资格行，按资格 ID 升序。 */
    public List<QualificationRow> qualificationsOf(String roleType, String roleId) {
        return jdbc.query("SELECT * FROM t_role_qualification WHERE role_type=? AND role_id=? ORDER BY qualification_id",
            QualificationTestMapper::qualificationRow, roleType, roleId);
    }

    /** 按资格 ID 读取；不存在返回 null。 */
    public QualificationRow findQualification(long qualificationId) {
        return jdbc.query("SELECT * FROM t_role_qualification WHERE qualification_id=?", QualificationTestMapper::qualificationRow,
            qualificationId).stream().findFirst().orElse(null);
    }

    /** 某角色的全部资格决定，按决定 ID 升序。 */
    public List<DecisionRow> decisionsOf(String roleType, String roleId) {
        return jdbc.query("SELECT * FROM t_qualification_decision WHERE role_type=? AND role_id=? ORDER BY decision_id",
            QualificationTestMapper::decisionRow, roleType, roleId);
    }

    /** 按决定 ID 读取；不存在返回 null。 */
    public DecisionRow findDecision(long decisionId) {
        return jdbc.query("SELECT * FROM t_qualification_decision WHERE decision_id=?", QualificationTestMapper::decisionRow,
            decisionId).stream().findFirst().orElse(null);
    }

    /** 某资格的全部状态变更，按聚合版本升序。 */
    public List<StateChangeRow> stateChanges(long qualificationId) {
        return jdbc.query("SELECT * FROM t_qualification_state_change WHERE qualification_id=? ORDER BY aggregate_version",
            (rs, i) -> new StateChangeRow(rs.getLong("change_id"), rs.getLong("qualification_id"), rs.getString("from_status"),
                rs.getString("to_status"), rs.getLong("decision_id"), rs.getString("reason_code"), rs.getString("trigger_type"),
                rs.getString("trigger_id"), rs.getObject("actor_id", Long.class), rs.getLong("aggregate_version"),
                instant(rs, "occurred_time")), qualificationId);
    }

    /** 三张资格表的总行数，用于断言被拒绝的命令没有写入。 */
    public long countQualificationRows() {
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM t_role_qualification) + (SELECT COUNT(*) FROM "
            + "t_qualification_decision) + (SELECT COUNT(*) FROM t_qualification_state_change)", Long.class);
    }

    private static QualificationRow qualificationRow(ResultSet rs, int rowNum) throws SQLException {
        return new QualificationRow(rs.getLong("qualification_id"), rs.getString("role_type"), rs.getString("role_id"),
            rs.getString("qualification_type"), rs.getLong("current_decision_id"), rs.getString("status"),
            instant(rs, "valid_from"), instant(rs, "valid_to"), rs.getString("review_frequency_code"),
            instant(rs, "next_review_time"), rs.getString("review_status"), rs.getLong("version"), instant(rs, "update_time"));
    }

    private static DecisionRow decisionRow(ResultSet rs, int rowNum) throws SQLException {
        return new DecisionRow(rs.getLong("decision_id"), rs.getObject("case_id", Long.class),
            rs.getObject("decision_version", Integer.class), rs.getString("role_type"), rs.getString("role_id"),
            rs.getString("qualification_type"), rs.getString("decision_source"), rs.getString("outcome"),
            rs.getString("override_reason_code"), rs.getString("basis_note"), instant(rs, "valid_from"), instant(rs, "valid_to"),
            rs.getObject("decided_by", Long.class), instant(rs, "decided_time"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
