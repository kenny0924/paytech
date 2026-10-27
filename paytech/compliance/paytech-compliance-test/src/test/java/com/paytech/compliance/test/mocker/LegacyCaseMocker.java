package com.paytech.compliance.test.mocker;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.compliance.test.mapper.ProgramTestMapper;

/**
 * 迁移后旧案件夹具（发布决策 P-8）
 * <li>按 changes/2026-10-27-onboarding-global.sql 对存量行的回填口径插入一个已批准旧案件：程序与版本 RETIRED、辖区 LEGACY、档次 STANDARD、
 * 轮次 1、无评级与推荐；证据地址由旧 file_id 拼成 /files/v1/{fileId}；资格决定与角色资格的新增列为空。</li>
 * <li>数据由全局清理器在每例结束时删除，本 Mocker 不持有状态。</li>
 */
@TestComponent
public class LegacyCaseMocker {
    /** 迁移回填的旧案件辖区。 */
    public static final String LEGACY_JURISDICTION = "LEGACY";

    private final ProgramTestMapper mapper;

    /** 注入测试 SQL 执行器。 */
    public LegacyCaseMocker(ProgramTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 已插入的旧案件
     *
     * @param caseId 案件 ID
     * @param applicationId 来源申请 ID
     * @param clientId Client ID
     * @param fileAddress 证据文件地址
     */
    public record LegacyCase(long caseId, long applicationId, long clientId, String fileAddress) {
    }

    /** 插入一个迁移后的已批准旧机构案件。 */
    public LegacyCase mockApprovedOrganization() {
        long base = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L) * 100;
        long program = base + 1;
        long version = base + 2;
        long requirementTemplate = base + 3;
        long caseId = base + 4;
        long clientId = base + 5;
        long applicationId = base + 6;
        long subject = base + 7;
        long requirement = base + 8;
        long decision = base + 9;
        long qualification = base + 10;
        String fileAddress = "/files/v1/" + (base + 11);
        String now = "UTC_TIMESTAMP(6) - INTERVAL 1 DAY";
        for (String statement : List.of(
            "INSERT INTO t_dd_program(program_id,program_code,qualification_type,jurisdiction_code,party_type,role_type,tier,owner_partner_id,"
                + "status,create_time,update_time) VALUES (" + program + ",'CLIENT_ORGANIZATION_GENERIC_" + program + "','ORGANIZATION_KYB',"
                + "NULL,'ORGANIZATION','CLIENT','STANDARD',NULL,'RETIRED'," + now + "," + now + ")",
            "INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_interval_months,four_eyes_required,status,"
                + "active_slot,approved_by,approved_time,create_time,update_time) VALUES (" + version + "," + program + ",1,NULL,1,'RETIRED',"
                + "NULL," + ProgramPackMocker.APPROVER + "," + now + "," + now + "," + now + ")",
            "INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,subject_role,required_flag,"
                + "max_age_days,waivable_flag,create_time) VALUES (" + requirementTemplate + "," + version + ",'REGISTRATION','PRIMARY',1,NULL,0,"
                + now + ")",
            "INSERT INTO t_dd_program_requirement_doc_type(requirement_doc_type_id,program_requirement_id,document_type,create_time) VALUES ("
                + requirementTemplate + "," + requirementTemplate + ",'REGISTRATION_CERTIFICATE'," + now + ")",
            "INSERT INTO t_due_diligence_case(case_id,case_no,case_type,role_type,role_id,qualification_type,dd_program_version_id,"
                + "jurisdiction_code,tier,case_origin,source_namespace,source_application_id,source_submission_id,round_no,status,active_slot,"
                + "submitted_time,decision_time,version,create_time,update_time) VALUES (" + caseId + ",'DD" + caseId + "','ONBOARDING','CLIENT',"
                + clientId + ",'ORGANIZATION_KYB'," + version + ",'" + LEGACY_JURISDICTION + "','STANDARD','APPLICATION',"
                + "'CCT_CLIENT_APPLICATION'," + applicationId + ",'legacy-" + caseId + "',1,'APPROVED',NULL," + now + "," + now + ",5," + now
                + "," + now + ")",
            "INSERT INTO t_dd_case_subject(case_subject_id,case_id,party_id,subject_role,primary_slot,source_profile_id,required_flag,"
                + "create_time) VALUES (" + subject + "," + caseId + "," + (base + 12) + ",'PRIMARY',1," + (base + 13) + ",1," + now + ")",
            "INSERT INTO t_dd_requirement(requirement_id,case_id,program_requirement_id,status,version,create_time,update_time) VALUES ("
                + requirement + "," + caseId + "," + requirementTemplate + ",'ACCEPTED',2," + now + "," + now + ")",
            "INSERT INTO t_dd_evidence_ref(evidence_id,requirement_id,case_subject_id,file_address,document_type,content_hash,status,"
                + "version,create_time,update_time) VALUES (" + (base + 14) + "," + requirement + "," + subject + ",'" + fileAddress + "',"
                + "'REGISTRATION_CERTIFICATE',REPEAT('a',64),'ACCEPTED',2," + now + "," + now + ")",
            "INSERT INTO t_dd_case_state_change(change_id,case_id,from_status,to_status,action_type,trigger_type,trigger_id,actor_id,"
                + "aggregate_version,occurred_time,create_time) VALUES (" + (base + 15) + "," + caseId + ",'IN_REVIEW','APPROVED','APPROVE',"
                + "'OPERATIONS_COMMAND','" + ProgramPackMocker.APPROVER + "'," + ProgramPackMocker.APPROVER + ",5," + now + "," + now + ")",
            "INSERT INTO t_qualification_decision(decision_id,case_id,role_type,role_id,qualification_type,decision_source,outcome,"
                + "valid_from,decided_by,decided_time,create_time) VALUES (" + decision + "," + caseId + ",'CLIENT'," + clientId
                + ",'ORGANIZATION_KYB','REVIEWER','APPROVED'," + now + "," + ProgramPackMocker.APPROVER + "," + now + "," + now + ")",
            "INSERT INTO t_role_qualification(qualification_id,role_type,role_id,qualification_type,current_decision_id,status,valid_from,"
                + "review_status,version,create_time,update_time) VALUES (" + qualification + ",'CLIENT'," + clientId + ",'ORGANIZATION_KYB',"
                + decision + ",'APPROVED'," + now + ",'NOT_REQUIRED',1," + now + "," + now + ")",
            "INSERT INTO t_qualification_state_change(change_id,qualification_id,from_status,to_status,decision_id,trigger_type,trigger_id,"
                + "actor_id,aggregate_version,occurred_time,create_time) VALUES (" + (base + 16) + "," + qualification + ",NULL,'APPROVED',"
                + decision + ",'CASE_DECISION','" + caseId + "'," + ProgramPackMocker.APPROVER + ",1," + now + "," + now + ")")) {
            mapper.execute(statement);
        }
        return new LegacyCase(caseId, applicationId, clientId, fileAddress);
    }
}
