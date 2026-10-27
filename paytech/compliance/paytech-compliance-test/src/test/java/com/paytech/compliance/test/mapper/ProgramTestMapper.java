package com.paytech.compliance.test.mapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** 尽调程序目录的测试 SQL：插入夹具程序、执行初始化 DML 的 INSERT 语句、读取模板 ID。 */
@TestComponent
public class ProgramTestMapper {
    private final JdbcTemplate jdbc;

    /** 注入测试库 JDBC。 */
    public ProgramTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 执行一条已替换变量的初始化 DML 语句。 */
    public void execute(String statement) {
        jdbc.update(statement);
    }

    /** 插入程序。 */
    public void insertProgram(long programId, String code, String qualificationType, String jurisdiction, String partyType,
        String roleType, String tier, Long owner, String status) {
        jdbc.update("INSERT INTO t_dd_program(program_id,program_code,qualification_type,jurisdiction_code,party_type,role_type,tier,"
            + "owner_partner_id,status,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", programId,
            code, qualificationType, jurisdiction, partyType, roleType, tier, owner, status);
    }

    /** 插入版本；approvedBy 为空时不写批准时间。 */
    public void insertVersion(long versionId, long programId, int versionNo, Integer interval, boolean fourEyes, String status,
        Long approvedBy) {
        jdbc.update("INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_interval_months,four_eyes_required,"
            + "status,active_slot,approved_by,approved_time,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,"
            + "IF(? IS NULL,NULL,UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", versionId, programId,
            versionNo, interval, fourEyes ? 1 : 0, status, "ACTIVE".equals(status) ? 1 : null, approvedBy, approvedBy);
    }

    /** 插入材料槽。 */
    public void insertRequirement(long id, long versionId, String code, boolean required, Integer maxAgeDays, boolean waivable) {
        jdbc.update("INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,subject_role,"
            + "required_flag,max_age_days,waivable_flag,create_time) VALUES(?,?,?,'PRIMARY',?,?,?,UTC_TIMESTAMP(6))", id, versionId, code,
            required ? 1 : 0, maxAgeDays, waivable ? 1 : 0);
    }

    /** 插入材料槽允许类型。 */
    public void insertDocType(long id, long requirementId, String type) {
        jdbc.update("INSERT INTO t_dd_program_requirement_doc_type(requirement_doc_type_id,program_requirement_id,document_type,"
            + "create_time) VALUES(?,?,?,UTC_TIMESTAMP(6))", id, requirementId, type);
    }

    /** 插入人工检查模板。 */
    public void insertCheck(long id, long versionId, String code, boolean blocking, Integer validityDays) {
        jdbc.update("INSERT INTO t_dd_program_check(program_check_id,program_version_id,check_code,check_type,check_mode,subject_role,"
            + "standard_api_code,required_flag,blocking_flag,validity_days,create_time) VALUES(?,?,?,'BUSINESS','MANUAL','PRIMARY',NULL,1,?,?,"
            + "UTC_TIMESTAMP(6))", id, versionId, code, blocking ? 1 : 0, validityDays);
    }

    /** 插入筛查模板。 */
    public void insertScreening(long id, long versionId, String type) {
        jdbc.update("INSERT INTO t_dd_program_screening(program_screening_id,program_version_id,subject_role,screening_type,"
            + "standard_api_code,required_flag,monitoring_required,create_time) VALUES(?,?,'PRIMARY',?,'SCREENING_SEARCH',1,0,"
            + "UTC_TIMESTAMP(6))", id, versionId, type);
    }

    /** 把程序的生效版本退役（发布新版本前）。 */
    public void retireVersion(long versionId) {
        jdbc.update("UPDATE t_dd_program_version SET status='RETIRED', active_slot=NULL WHERE program_version_id=?", versionId);
    }

    /** 按程序编码读取 ACTIVE 版本 ID。 */
    public long activeVersionId(String programCode) {
        return jdbc.queryForObject("SELECT v.program_version_id FROM t_dd_program p JOIN t_dd_program_version v ON v.program_id=p.program_id "
            + "WHERE p.program_code=? AND v.active_slot=1", Long.class, programCode);
    }

    /** 按程序编码读取 program_id。 */
    public long programId(String programCode) {
        return jdbc.queryForObject("SELECT program_id FROM t_dd_program WHERE program_code=?", Long.class, programCode);
    }

    /** 某版本各要求代码的允许类型（按类型排序），用于 DML 与解析结果一致性断言。 */
    public Map<String, List<String>> docTypes(long versionId) {
        return jdbc.query("SELECT r.requirement_code,d.document_type FROM t_dd_program_requirement r JOIN t_dd_program_requirement_doc_type d "
            + "ON d.program_requirement_id=r.program_requirement_id WHERE r.program_version_id=? ORDER BY r.requirement_code,d.document_type",
            (rs, i) -> Map.entry(rs.getString(1), rs.getString(2)), versionId).stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    /** 程序数。 */
    public long programCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_dd_program", Long.class);
    }
}
