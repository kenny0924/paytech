package com.paytech.risk.test.mapper.decision;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;

/**
 * TEST Candidate 发布根、审计事件与旧发布组成补充行的测试 SQL
 * <li>规则集、版本、决策点版本与绑定沿用 {@link RuleSetTestMapper}；本类补充输入字段、字符串 GT 规则、发布与审计。</li>
 * <li>只按调用方给出的 ID 读写和删除，不清理其他测试或会话插入的数据。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ReleaseTestMapper {

    private final JdbcTemplate jdbc;

    public ReleaseTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 持久化的发布根。 */
    public record ReleaseRow(long releaseId, String releaseNo, long decisionPointId, long pointVersionId,
        String environmentCode, String status, Integer activeSlot, String contentHash, long createdBy,
        Instant activatedTime, Instant supersededTime, int version, Instant createTime, Instant updateTime) {
    }

    /** 持久化的审计事件；状态事件只有状态字段，检查事件只有检查字段。 */
    public record AuditRow(long auditEventId, long releaseId, ReleaseAuditEventType eventType, Instant eventTime,
        StaticCheckCode checkCode, StaticCheckStatus checkStatus, StaticCheckSeverity severity, String errorCode,
        String errorSummary, DecisionReleaseStatus fromStatus, DecisionReleaseStatus toStatus, String reasonCode,
        String triggerType, String triggerId, Long actorId, Integer aggregateVersion, Instant createTime) {
    }

    /** 插入一个决策点版本的输入字段。 */
    public void insertInputField(long inputFieldId, long pointVersionId, InputFieldDescriptor field) {
        jdbc.update("INSERT INTO t_decision_point_input_field(input_field_id,point_version_id,field_code,value_type,"
            + "required_flag,data_classification,rule_reference_flag,action_parameter_flag,create_time) "
            + "VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", inputFieldId, pointVersionId, field.fieldCode(),
            field.valueType().getCode(), field.requiredFlag(), field.dataClassification().getCode(),
            field.ruleReferenceFlag(), field.actionParameterFlag());
    }

    /** 插入 country_code GT "AE" 规则：规则、谓词节点、左右操作数与终止动作，ID 为 base..base+4。 */
    public void insertOrderedStringRule(long ruleId, long versionId, long actionVersionId) {
        jdbc.update("INSERT INTO t_risk_rule(rule_id,rule_set_version_id,rule_code,rule_name,priority_no,enabled_flag,"
            + "reason_code,reason_message,create_time,update_time) VALUES(?,?,'MOCK_RULE','Mock rule',10,1,NULL,NULL,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId, versionId);
        jdbc.update("INSERT INTO t_rule_node(node_id,rule_id,parent_node_id,node_type,group_operator,"
            + "comparison_operator,sequence_no,create_time,update_time) VALUES(?,?,NULL,'PREDICATE',NULL,'GT',1,"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 1, ruleId);
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,field_code,"
            + "value_type,create_time,update_time) VALUES(?,?,'LEFT',1,'CONTEXT','country_code','STRING',"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 2, ruleId + 1);
        jdbc.update("INSERT INTO t_rule_action(rule_action_id,rule_id,action_version_id,sequence_no,target_binding_code,"
            + "create_time) VALUES(?,?,?,1,NULL,UTC_TIMESTAMP(6))", ruleId + 3, ruleId, actionVersionId);
        jdbc.update("INSERT INTO t_rule_operand(operand_id,node_id,operand_role,value_sequence,source_type,value_type,"
            + "string_value,create_time,update_time) VALUES(?,?,'RIGHT',1,'LITERAL','STRING','AE',"
            + "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", ruleId + 4, ruleId + 1);
    }

    /** 插入发布根；首期 Candidate 没有生效槽与激活时间。 */
    public void insertRelease(long releaseId, String releaseNo, long pointId, long pointVersionId,
        DecisionEnvironment environment, DecisionReleaseStatus status, String contentHash, long createdBy, int version,
        Instant createTime, Instant updateTime) {
        jdbc.update("INSERT INTO t_decision_release(release_id,release_no,decision_point_id,point_version_id,"
            + "environment_code,status,active_slot,content_hash,created_by,activated_time,superseded_time,version,"
            + "create_time,update_time) VALUES(?,?,?,?,?,?,NULL,?,?,NULL,NULL,?,?,?)", releaseId, releaseNo, pointId,
            pointVersionId, environment.getCode(), status.getCode(), contentHash, createdBy, version, ts(createTime),
            ts(updateTime));
    }

    /** 插入一条审计事件。 */
    public void insertAudit(AuditRow row) {
        jdbc.update("INSERT INTO t_release_audit_event(audit_event_id,release_id,event_type,event_time,check_code,"
            + "check_status,severity,error_code,error_summary,from_status,to_status,reason_code,trigger_type,trigger_id,"
            + "actor_id,aggregate_version,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", row.auditEventId(),
            row.releaseId(), row.eventType().getCode(), ts(row.eventTime()),
            row.checkCode() == null ? null : row.checkCode().getCode(),
            row.checkStatus() == null ? null : row.checkStatus().getCode(),
            row.severity() == null ? null : row.severity().getCode(), row.errorCode(), row.errorSummary(),
            row.fromStatus() == null ? null : row.fromStatus().getCode(),
            row.toStatus() == null ? null : row.toStatus().getCode(), row.reasonCode(), row.triggerType(),
            row.triggerId(), row.actorId(), row.aggregateVersion(), ts(row.createTime()));
    }

    /** 改写规则集版本的内容格式。 */
    public void updateContentFormat(long ruleSetVersionId, String contentFormat) {
        jdbc.update("UPDATE t_rule_set_version SET content_format=? WHERE rule_set_version_id=?", contentFormat,
            ruleSetVersionId);
    }

    /** 覆盖发布内容摘要。 */
    public void updateReleaseContentHash(long releaseId, String contentHash) {
        jdbc.update("UPDATE t_decision_release SET content_hash=? WHERE release_id=?", contentHash, releaseId);
    }

    /** 覆盖决策点版本内容摘要。 */
    public void updatePointVersionHash(long pointVersionId, String contentHash) {
        jdbc.update("UPDATE t_decision_point_version SET content_hash=? WHERE point_version_id=?", contentHash,
            pointVersionId);
    }

    /** 改写决策点版本状态。 */
    public void updatePointVersionStatus(long pointVersionId, DecisionPointVersionStatus status) {
        jdbc.update("UPDATE t_decision_point_version SET status=? WHERE point_version_id=?", status.getCode(),
            pointVersionId);
    }

    /** 删除一条绑定。 */
    public void deleteBinding(long bindingId) {
        jdbc.update("DELETE FROM t_decision_point_rule_set WHERE binding_id=?", bindingId);
    }

    /** 删除一条审计事件。 */
    public void deleteAudit(long auditEventId) {
        jdbc.update("DELETE FROM t_release_audit_event WHERE audit_event_id=?", auditEventId);
    }

    /** 读取发布根，不存在时为 null。 */
    public ReleaseRow findRelease(long releaseId) {
        List<ReleaseRow> rows = jdbc.query("SELECT release_id,release_no,decision_point_id,point_version_id,"
            + "environment_code,status,active_slot,content_hash,created_by,activated_time,superseded_time,version,"
            + "create_time,update_time FROM t_decision_release WHERE release_id=?", (rs, ignored) -> new ReleaseRow(
                rs.getLong("release_id"), rs.getString("release_no"), rs.getLong("decision_point_id"),
                rs.getLong("point_version_id"), rs.getString("environment_code"), rs.getString("status"),
                rs.getObject("active_slot", Integer.class), rs.getString("content_hash"), rs.getLong("created_by"),
                instant(rs, "activated_time"), instant(rs, "superseded_time"), rs.getInt("version"),
                instant(rs, "create_time"), instant(rs, "update_time")), releaseId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 引用指定决策点版本的发布 ID，按 ID 升序。 */
    public List<Long> findReleaseIds(long pointVersionId) {
        return jdbc.queryForList("SELECT release_id FROM t_decision_release WHERE point_version_id=? "
            + "ORDER BY release_id", Long.class, pointVersionId);
    }

    /** 状态事件按聚合版本升序。 */
    public List<AuditRow> findStateChanges(long releaseId) {
        return findAudits(releaseId, ReleaseAuditEventType.STATE_CHANGE).stream()
            .sorted(Comparator.comparing(AuditRow::aggregateVersion)).toList();
    }

    /** 检查事件按八项固定顺序。 */
    public List<AuditRow> findStaticChecks(long releaseId) {
        return findAudits(releaseId, ReleaseAuditEventType.STATIC_CHECK).stream()
            .sorted(Comparator.comparing(AuditRow::checkCode)).toList();
    }

    /** 本发布的全部审计事件数量。 */
    public long countAudits(long releaseId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_release_audit_event WHERE release_id=?", Long.class,
            releaseId);
    }

    /** 删除指定发布的审计事件与根。 */
    public void deleteReleases(Collection<Long> releaseIds) {
        releaseIds.forEach(id -> jdbc.update("DELETE FROM t_release_audit_event WHERE release_id=?", id));
        releaseIds.forEach(id -> jdbc.update("DELETE FROM t_decision_release WHERE release_id=?", id));
    }

    /** 删除指定决策点版本的输入字段。 */
    public void deleteInputFields(Collection<Long> pointVersionIds) {
        pointVersionIds
            .forEach(id -> jdbc.update("DELETE FROM t_decision_point_input_field WHERE point_version_id=?", id));
    }

    private List<AuditRow> findAudits(long releaseId, ReleaseAuditEventType type) {
        return jdbc.query("SELECT audit_event_id,release_id,event_type,event_time,check_code,check_status,severity,"
            + "error_code,error_summary,from_status,to_status,reason_code,trigger_type,trigger_id,actor_id,"
            + "aggregate_version,create_time FROM t_release_audit_event WHERE release_id=? AND event_type=?",
            (rs, ignored) -> new AuditRow(rs.getLong("audit_event_id"), rs.getLong("release_id"),
                ReleaseAuditEventType.valueOf(rs.getString("event_type")), instant(rs, "event_time"),
                code(StaticCheckCode.class, rs.getString("check_code")),
                code(StaticCheckStatus.class, rs.getString("check_status")),
                code(StaticCheckSeverity.class, rs.getString("severity")), rs.getString("error_code"),
                rs.getString("error_summary"), code(DecisionReleaseStatus.class, rs.getString("from_status")),
                code(DecisionReleaseStatus.class, rs.getString("to_status")), rs.getString("reason_code"),
                rs.getString("trigger_type"), rs.getString("trigger_id"), rs.getObject("actor_id", Long.class),
                rs.getObject("aggregate_version", Integer.class), instant(rs, "create_time")),
            releaseId, type.getCode());
    }

    private static <E extends Enum<E>> E code(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
