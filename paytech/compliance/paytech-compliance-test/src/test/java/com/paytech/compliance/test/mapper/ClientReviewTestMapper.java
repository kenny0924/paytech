package com.paytech.risk.test.mapper.compliance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Client 入驻审核九张表（案件、主体、材料要求、证据、依赖、状态变更、分类、资格决定、补偿任务）的测试 SQL
 * <li>写入只供本地 Mocker 准备前置事实；读取供 Checker 核对落库结果，时间一律按 UTC 读写。</li>
 * <li>删除只按调用方给出的 CCT 申请 ID 进行，不清理其他测试或会话的审核数据。</li>
 *
 * @author Kenny
 */
@TestComponent
public class ClientReviewTestMapper {

    /** 审核结果补偿任务的事件类型。 */
    public static final String RESULT_EVENT_TYPE = "CLIENT_REVIEW_RESULT";

    private final JdbcTemplate jdbc;

    public ClientReviewTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 案件行。 */
    public record CaseRow(long caseId, String caseNo, String roleType, long roleId, String qualificationType,
        long programVersionId, String caseOrigin, String sourceNamespace, Long applicationId, String submissionId,
        String publicNote, String resolutionNote, String returnSection, String status, Integer activeSlot,
        Instant submittedTime, Instant decisionTime, long version) {}

    /** 案件主体行。 */
    public record SubjectRow(long caseSubjectId, long caseId, long partyId, String subjectRole, Integer primarySlot,
        long sourceProfileId, Long sourceRelationId, Object ownershipPercentage, int requiredFlag) {}

    /** 材料要求行及其程序模板的要求代码。 */
    public record RequirementRow(long requirementId, long caseId, long programRequirementId, String requirementCode,
        String status, long version) {}

    /** 证据引用行。 */
    public record EvidenceRow(long evidenceId, long requirementId, long caseSubjectId, long fileId, String contentHash,
        String status, long version) {}

    /** 案件依赖行。 */
    public record DependencyRow(long dependencyId, long parentCaseId, long childCaseId, String dependencyType,
        int requiredFlag, Long acceptedDecisionId, long version) {}

    /** 状态变更行。 */
    public record StateChangeRow(long changeId, long caseId, String fromStatus, String toStatus, String actionType,
        String reasonCode, String triggerType, String triggerId, Long actorId, long aggregateVersion,
        Instant occurredTime) {}

    /** 分类行。 */
    public record ClassificationRow(long classificationId, long caseId, String industryCode, String mccCode,
        Integer primarySlot, long confirmedBy, Instant confirmedTime) {}

    /** 资格决定行。 */
    public record DecisionRow(long decisionId, long caseId, Integer decisionVersion, String roleType, long roleId,
        String qualificationType, String decisionSource, String outcome, Instant validFrom, Instant validTo,
        Long decidedBy, Instant decidedTime) {}

    /** 审核结果补偿任务行。 */
    public record EventRow(long id, String eventKey, String subEventType, String payloadJson, String executeStatus) {}

    /** 插入案件。 */
    public void insertCase(CaseRow row) {
        jdbc.update("INSERT INTO t_due_diligence_case(case_id,case_no,role_type,role_id,qualification_type,"
            + "dd_program_version_id,case_origin,source_namespace,source_application_id,source_submission_id,public_note,"
            + "resolution_note,return_section,status,active_slot,submitted_time,decision_time,version,create_time,update_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", row.caseId(), row.caseNo(),
            row.roleType(), row.roleId(), row.qualificationType(), row.programVersionId(), row.caseOrigin(),
            row.sourceNamespace(), row.applicationId(), row.submissionId(), row.publicNote(), row.resolutionNote(),
            row.returnSection(), row.status(), row.activeSlot(), utc(row.submittedTime()), utc(row.decisionTime()),
            row.version());
    }

    /** 插入案件 PRIMARY 主体。 */
    public void insertSubject(SubjectRow row) {
        jdbc.update("INSERT INTO t_dd_case_subject(case_subject_id,case_id,party_id,subject_role,primary_slot,"
            + "source_profile_id,required_flag,create_time) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", row.caseSubjectId(),
            row.caseId(), row.partyId(), row.subjectRole(), row.primarySlot(), row.sourceProfileId(), row.requiredFlag());
    }

    /** 插入材料要求。 */
    public void insertRequirement(RequirementRow row) {
        jdbc.update("INSERT INTO t_dd_requirement(requirement_id,case_id,program_requirement_id,status,version,"
            + "create_time,update_time) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", row.requirementId(),
            row.caseId(), row.programRequirementId(), row.status(), row.version());
    }

    /** 插入证据引用。 */
    public void insertEvidence(EvidenceRow row) {
        jdbc.update("INSERT INTO t_dd_evidence_ref(evidence_id,requirement_id,case_subject_id,file_id,content_hash,"
            + "status,version,create_time,update_time) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            row.evidenceId(), row.requirementId(), row.caseSubjectId(), row.fileId(), row.contentHash(), row.status(),
            row.version());
    }

    /** 插入主案件对子案件的依赖。 */
    public void insertDependency(DependencyRow row) {
        jdbc.update("INSERT INTO t_dd_case_dependency(dependency_id,parent_case_id,child_case_id,dependency_type,"
            + "required_flag,version,create_time,update_time) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            row.dependencyId(), row.parentCaseId(), row.childCaseId(), row.dependencyType(), row.requiredFlag(),
            row.version());
    }

    /** 插入状态变更。 */
    public void insertStateChange(StateChangeRow row) {
        jdbc.update("INSERT INTO t_dd_case_state_change(change_id,case_id,from_status,to_status,action_type,reason_code,"
            + "trigger_type,trigger_id,actor_id,aggregate_version,occurred_time,create_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", row.changeId(), row.caseId(), row.fromStatus(),
            row.toStatus(), row.actionType(), row.reasonCode(), row.triggerType(), row.triggerId(), row.actorId(),
            row.aggregateVersion(), utc(row.occurredTime()));
    }

    /** 插入已接收分类。 */
    public void insertClassification(ClassificationRow row) {
        jdbc.update("INSERT INTO t_dd_case_classification(case_classification_id,case_id,industry_code,mcc_code,"
            + "primary_slot,confirmed_by,confirmed_time,create_time) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            row.classificationId(), row.caseId(), row.industryCode(), row.mccCode(), row.primarySlot(), row.confirmedBy(),
            utc(row.confirmedTime()));
    }

    /** 插入资格决定。 */
    public void insertDecision(DecisionRow row) {
        jdbc.update("INSERT INTO t_qualification_decision(decision_id,case_id,decision_version,role_type,role_id,"
            + "qualification_type,decision_source,outcome,valid_from,valid_to,decided_by,decided_time,create_time) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))", row.decisionId(), row.caseId(), row.decisionVersion(),
            row.roleType(), row.roleId(), row.qualificationType(), row.decisionSource(), row.outcome(),
            utc(row.validFrom()), utc(row.validTo()), row.decidedBy(), utc(row.decidedTime()));
    }

    /** 某程序修订下各材料要求代码对应的程序要求 ID。 */
    public Map<String, Long> programRequirementIds(long programVersionId) {
        return jdbc.query("SELECT requirement_code,program_requirement_id FROM t_dd_program_requirement "
            + "WHERE program_version_id=?", (rs, i) -> Map.entry(rs.getString(1), rs.getLong(2)), programVersionId)
            .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 某 CCT 申请下的全部案件，按案件 ID 升序。 */
    public List<CaseRow> casesOfApplication(long applicationId) {
        return jdbc.query("SELECT * FROM t_due_diligence_case WHERE source_application_id=? ORDER BY case_id",
            ClientReviewTestMapper::caseRow, applicationId);
    }

    /** 按 ID 读取案件；不存在返回 null。 */
    public CaseRow findCase(long caseId) {
        List<CaseRow> rows = jdbc.query("SELECT * FROM t_due_diligence_case WHERE case_id=?",
            ClientReviewTestMapper::caseRow, caseId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 若干案件的主体，按案件与主体 ID 升序。 */
    public List<SubjectRow> subjects(Collection<Long> caseIds) {
        return jdbc.query("SELECT * FROM t_dd_case_subject WHERE case_id IN (" + in(caseIds) + ") ORDER BY case_id,"
            + "case_subject_id", (rs, i) -> new SubjectRow(rs.getLong("case_subject_id"), rs.getLong("case_id"),
                rs.getLong("party_id"), rs.getString("subject_role"), (Integer)rs.getObject("primary_slot", Integer.class),
                rs.getLong("source_profile_id"), rs.getObject("source_relation_id", Long.class),
                rs.getObject("ownership_percentage"), rs.getInt("required_flag")), caseIds.toArray());
    }

    /** 若干案件的材料要求，按案件与要求代码升序。 */
    public List<RequirementRow> requirements(Collection<Long> caseIds) {
        return jdbc.query("SELECT r.*,p.requirement_code FROM t_dd_requirement r JOIN t_dd_program_requirement p "
            + "ON p.program_requirement_id=r.program_requirement_id WHERE r.case_id IN (" + in(caseIds) + ") "
            + "ORDER BY r.case_id,p.requirement_code", (rs, i) -> new RequirementRow(rs.getLong("requirement_id"),
                rs.getLong("case_id"), rs.getLong("program_requirement_id"), rs.getString("requirement_code"),
                rs.getString("status"), rs.getLong("version")), caseIds.toArray());
    }

    /** 若干案件下的证据引用，按证据 ID 升序。 */
    public List<EvidenceRow> evidence(Collection<Long> caseIds) {
        return jdbc.query("SELECT e.* FROM t_dd_evidence_ref e JOIN t_dd_requirement r ON r.requirement_id=e.requirement_id "
            + "WHERE r.case_id IN (" + in(caseIds) + ") ORDER BY e.evidence_id", (rs, i) -> new EvidenceRow(
                rs.getLong("evidence_id"), rs.getLong("requirement_id"), rs.getLong("case_subject_id"),
                rs.getLong("file_id"), rs.getString("content_hash"), rs.getString("status"), rs.getLong("version")),
            caseIds.toArray());
    }

    /** 主案件对子案件的依赖，按依赖 ID 升序。 */
    public List<DependencyRow> dependencies(long parentCaseId) {
        return jdbc.query("SELECT * FROM t_dd_case_dependency WHERE parent_case_id=? ORDER BY dependency_id",
            (rs, i) -> new DependencyRow(rs.getLong("dependency_id"), rs.getLong("parent_case_id"),
                rs.getLong("child_case_id"), rs.getString("dependency_type"), rs.getInt("required_flag"),
                rs.getObject("accepted_decision_id", Long.class), rs.getLong("version")), parentCaseId);
    }

    /** 若干案件的状态变更，按案件、聚合版本与变更 ID 升序。 */
    public List<StateChangeRow> stateChanges(Collection<Long> caseIds) {
        return jdbc.query("SELECT * FROM t_dd_case_state_change WHERE case_id IN (" + in(caseIds) + ") "
            + "ORDER BY case_id,aggregate_version,change_id", (rs, i) -> new StateChangeRow(rs.getLong("change_id"),
                rs.getLong("case_id"), rs.getString("from_status"), rs.getString("to_status"), rs.getString("action_type"),
                rs.getString("reason_code"), rs.getString("trigger_type"), rs.getString("trigger_id"),
                rs.getObject("actor_id", Long.class), rs.getLong("aggregate_version"), instant(rs, "occurred_time")),
            caseIds.toArray());
    }

    /** 某案件已接收的分类，按分类 ID 升序。 */
    public List<ClassificationRow> classifications(long caseId) {
        return jdbc.query("SELECT * FROM t_dd_case_classification WHERE case_id=? ORDER BY case_classification_id",
            (rs, i) -> new ClassificationRow(rs.getLong("case_classification_id"), rs.getLong("case_id"),
                rs.getString("industry_code"), rs.getString("mcc_code"), rs.getObject("primary_slot", Integer.class),
                rs.getLong("confirmed_by"), instant(rs, "confirmed_time")), caseId);
    }

    /** 若干案件的资格决定，按案件 ID 升序。 */
    public List<DecisionRow> decisions(Collection<Long> caseIds) {
        return jdbc.query("SELECT * FROM t_qualification_decision WHERE case_id IN (" + in(caseIds) + ") "
            + "ORDER BY case_id,decision_id", (rs, i) -> new DecisionRow(rs.getLong("decision_id"), rs.getLong("case_id"),
                rs.getObject("decision_version", Integer.class), rs.getString("role_type"), rs.getLong("role_id"),
                rs.getString("qualification_type"), rs.getString("decision_source"), rs.getString("outcome"),
                instant(rs, "valid_from"), instant(rs, "valid_to"), rs.getObject("decided_by", Long.class),
                instant(rs, "decided_time")), caseIds.toArray());
    }

    /** 某主案件的审核结果补偿任务。 */
    public List<EventRow> resultEvents(long caseId) {
        return jdbc.query("SELECT id,event_key,sub_event_type,payload_json,execute_status FROM t_compensation_event "
            + "WHERE event_type=? AND event_key=?", (rs, i) -> new EventRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), RESULT_EVENT_TYPE, Long.toString(caseId));
    }

    /** Client 入驻主案件中不属于给定申请集合的数量，按与列表相同的筛选条件统计。 */
    public long countOtherClientCases(String status, String qualificationType, Collection<Long> excludedApplications) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM t_due_diligence_case WHERE source_namespace="
            + "'CCT_CLIENT_APPLICATION' AND role_type='CLIENT'");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null) {
            sql.append(" AND status=?");
            args.add(status);
        }
        if (qualificationType != null) {
            sql.append(" AND qualification_type=?");
            args.add(qualificationType);
        }
        if (!excludedApplications.isEmpty()) {
            sql.append(" AND (source_application_id IS NULL OR source_application_id NOT IN (")
                .append(in(excludedApplications)).append("))");
            args.addAll(excludedApplications);
        }
        return jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    }

    /** 指定申请下仍在等待或执行中的审核结果补偿任务数。 */
    public int pendingResultEvents(Collection<Long> applicationIds) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_compensation_event e JOIN t_due_diligence_case c "
            + "ON e.event_key=CAST(c.case_id AS CHAR) WHERE e.event_type=? AND e.execute_status IN ('INIT','PROCESSING') "
            + "AND c.source_application_id IN (" + in(applicationIds) + ")", Integer.class,
            concat(RESULT_EVENT_TYPE, applicationIds));
    }

    /** 删除指定申请的全部审核行及其结果补偿任务。 */
    public void deleteApplications(Collection<Long> applicationIds) {
        for (Long applicationId : applicationIds) {
            List<Long> caseIds = jdbc.queryForList("SELECT case_id FROM t_due_diligence_case WHERE source_application_id=?",
                Long.class, applicationId);
            if (caseIds.isEmpty()) {
                continue;
            }
            String ids = in(caseIds);
            Object[] args = caseIds.toArray();
            jdbc.update("DELETE e FROM t_dd_evidence_ref e JOIN t_dd_requirement r ON r.requirement_id=e.requirement_id "
                + "WHERE r.case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_dd_requirement WHERE case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_dd_case_subject WHERE case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_dd_case_state_change WHERE case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_dd_case_classification WHERE case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_qualification_decision WHERE case_id IN (" + ids + ")", args);
            jdbc.update("DELETE FROM t_dd_case_dependency WHERE parent_case_id IN (" + ids + ") OR child_case_id IN ("
                + ids + ")", concat(null, caseIds, caseIds));
            jdbc.update("DELETE FROM t_compensation_event WHERE event_type=? AND event_key IN (" + ids + ")",
                concat(RESULT_EVENT_TYPE, caseIds.stream().map(String::valueOf).toList()));
            jdbc.update("DELETE FROM t_due_diligence_case WHERE case_id IN (" + ids + ")", args);
        }
    }

    private static CaseRow caseRow(ResultSet rs, int rowNum) throws SQLException {
        return new CaseRow(rs.getLong("case_id"), rs.getString("case_no"), rs.getString("role_type"), rs.getLong("role_id"),
            rs.getString("qualification_type"), rs.getLong("dd_program_version_id"), rs.getString("case_origin"),
            rs.getString("source_namespace"), rs.getObject("source_application_id", Long.class),
            rs.getString("source_submission_id"), rs.getString("public_note"), rs.getString("resolution_note"),
            rs.getString("return_section"), rs.getString("status"), rs.getObject("active_slot", Integer.class),
            instant(rs, "submitted_time"), instant(rs, "decision_time"), rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String in(Collection<?> values) {
        return String.join(",", java.util.Collections.nCopies(values.size(), "?"));
    }

    /** 拼接首个可选参数与若干参数集合。 */
    private static Object[] concat(Object first, Collection<?>... rest) {
        List<Object> args = new java.util.ArrayList<>();
        if (first != null) {
            args.add(first);
        }
        for (Collection<?> values : rest) {
            args.addAll(values);
        }
        return args.toArray();
    }
}
