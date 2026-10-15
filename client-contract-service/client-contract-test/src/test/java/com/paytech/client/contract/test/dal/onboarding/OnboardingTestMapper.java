package com.paytech.client.contract.test.dal.onboarding;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

/** 入驻测试夹具的插入、只读断言与按标识清理；只操作本测试登记的行。 */
@TestComponent
public class OnboardingTestMapper {
    private final JdbcTemplate jdbc;

    /** 使用 CCT 正式数据源。 */
    public OnboardingTestMapper(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 插入主体、资料头、个人或组织明细、地址与证件行。 */
    public void insertSubject(long partyId, long profileId, Long draftApplicationId, boolean organization, String addressType,
        long addressId, long identifierId, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party(party_id,party_no,party_type,create_time,update_time) VALUES(?,?,?,?,?)", partyId,
            "TP" + partyId, organization ? "ORGANIZATION" : "INDIVIDUAL", at, at);
        jdbc.update("INSERT INTO t_party_profile(profile_id,party_id,draft_application_id,create_time,update_time) VALUES(?,?,?,?,?)",
            profileId, partyId, draftApplicationId, at, at);
        if (organization) {
            jdbc.update("INSERT INTO t_organization_profile(profile_id,legal_name,trade_name,incorporation_country,organization_type,"
                + "incorporation_date,create_time,update_time) VALUES(?,?,?,?,?,?,?,?)", profileId, "Fixture Trading LLC", "Fixture",
                "AE", "LIMITED_COMPANY", java.sql.Date.valueOf("2020-01-15"), at, at);
        } else {
            jdbc.update("INSERT INTO t_individual_profile(profile_id,given_name,family_name,birth_date,nationality_country,"
                + "residence_country,create_time,update_time) VALUES(?,?,?,?,?,?,?,?)", profileId, "Alex", "Fixture",
                java.sql.Date.valueOf("1990-05-01"), "GB", "AE", at, at);
        }
        jdbc.update("INSERT INTO t_party_address(address_id,profile_id,address_type,address_line1,city,country_code,create_time,"
            + "update_time) VALUES(?,?,?,?,?,?,?,?)", addressId, profileId, addressType, "1 Fixture Road", "Dubai", "AE", at, at);
        jdbc.update("INSERT INTO t_party_profile_identifier(profile_identifier_id,profile_id,identifier_type,issuing_country,"
            + "identifier_value,create_time,update_time) VALUES(?,?,?,?,?,?,?)", identifierId, profileId,
            organization ? "COMPANY_REGISTRATION_NUMBER" : "PASSPORT", "GB", "p 12-" + partyId % 1_000_000, at, at);
    }

    /** 插入 Client、经营资料、申请与联系方式。 */
    public void insertApplication(long applicationId, long clientId, long partyId, long profileId, long businessProfileId,
        long accountId, boolean organization, long contactId, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_client(client_id,client_no,party_id,status,create_time,update_time) VALUES(?,?,?,'PENDING',?,?)",
            clientId, "TC" + clientId, partyId, at, at);
        jdbc.update("INSERT INTO t_client_business_profile(business_profile_id,client_id,business_model_code,website_url,"
            + "expected_volume,expected_ticket,currency,business_description,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
            businessProfileId, clientId, "SERVICES", "https://fixture.example.test", new java.math.BigDecimal("50000"),
            new java.math.BigDecimal("25.5"), "USD", "Fixture consulting services", at, at);
        jdbc.update("INSERT INTO t_client_application(application_id,application_no,client_id,requested_by_account_id,status,"
            + "active_slot,qualification_type,create_time,update_time) VALUES(?,?,?,?,'DRAFT',1,?,?,?)", applicationId,
            "TA" + applicationId, clientId, accountId, organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC", at, at);
        jdbc.update("INSERT INTO t_party_contact(contact_id,profile_id,contact_type,contact_vault_token,contact_masked_value,"
            + "create_time,update_time) VALUES(?,?,'EMAIL',?,?,?,?)", contactId, profileId, "vt_email_" + contactId, "f***@example.test", at, at);
        jdbc.update("INSERT INTO t_party_contact(contact_id,profile_id,contact_type,contact_vault_token,contact_masked_value,"
            + "create_time,update_time) VALUES(?,?,'PHONE',?,?,?,?)", contactId + 1, profileId, "vt_phone_" + contactId, "+971****0000", at, at);
    }

    /** 插入组织与人员的草稿关系。 */
    public void insertRelation(long relationId, long applicationId, long organizationPartyId, long personPartyId, String type,
        Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party_relation(relation_id,organization_party_id,related_party_id,relation_type,"
            + "ownership_percentage,effective_from,status,client_application_id,create_time,update_time) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?)",
            relationId, organizationPartyId, personPartyId, type, "UBO".equals(type) ? new java.math.BigDecimal("40") : null,
            Timestamp.from(Instant.parse("2024-01-01T00:00:00Z")), applicationId, at, at);
    }

    /** 插入材料引用。 */
    public void insertDocument(long documentId, long applicationId, long subjectPartyId, String requirementCode, String documentType,
        long fileId, String contentHash, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_application_document(document_id,client_application_id,subject_party_id,requirement_code,"
            + "document_type,file_id,content_hash,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)", documentId, applicationId,
            subjectPartyId, requirementCode, documentType, fileId, contentHash, at, at);
    }

    /** 插入属于其他主体的永久身份键。 */
    public void insertIdentityKey(long identityKeyId, long partyId, String type, String scope, String normalized, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party_identity_key(identity_key_id,party_id,identifier_type,issuing_country,issuer_scope_key,"
            + "normalized_identifier_value,create_time,update_time) VALUES(?,?,?,?,?,?,?,?)", identityKeyId, partyId, type,
            "GLOBAL".equals(scope) ? null : scope, scope, normalized, at, at);
    }

    /** 清空某张资料表的一列，用于构造缺项。 */
    public void clear(String table, String column, long profileId) {
        if (!table.matches("t_[a-z_]+") || !column.matches("[a-z_0-9]+")) {
            throw new IllegalArgumentException("unsafe identifier");
        }
        jdbc.update("UPDATE " + table + " SET " + column + "=NULL WHERE profile_id=?", profileId);
    }

    /** 把申请置为已登记审核：UNDER_REVIEW、当前提交与主审核任务。 */
    public void underReview(long applicationId, String submissionId, long caseId, Instant now) {
        jdbc.update("UPDATE t_client_application SET status='UNDER_REVIEW', current_submission_id=?, due_diligence_case_id=?, "
            + "submitted_time=?, update_time=? WHERE application_id=?", submissionId, caseId, Timestamp.from(now), Timestamp.from(now),
            applicationId);
    }

    /** 插入 Client 生命周期历史根与第一版（CREATE）。 */
    public void insertClientHistory(long historyId, long changeVersionId, long clientId, long accountId, String snapshot, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_business_change_record(history_id,record_type,record_id,aggregate_root_type,aggregate_root_id,"
            + "current_version,create_time,update_time) VALUES(?,'CLIENT',?,'CLIENT',?,1,?,?)", historyId, Long.toString(clientId),
            Long.toString(clientId), at, at);
        jdbc.update("INSERT INTO t_business_change_version(change_version_id,history_id,version_no,operation_type,snapshot_format,"
            + "snapshot_json,create_by,create_time) VALUES(?,?,1,'CREATE','ENTITY_NO',?,?,?)", changeVersionId, historyId, snapshot,
            accountId, at);
    }

    /** 申请审核结论相关列。 */
    public Map<String, Object> review(long applicationId) {
        return jdbc.queryForMap("SELECT status,active_slot,qualification_decision_id FROM t_client_application WHERE application_id=?",
            applicationId);
    }

    /** Client 状态。 */
    public String clientStatus(long clientId) {
        return jdbc.queryForObject("SELECT status FROM t_client WHERE client_id=?", String.class, clientId);
    }

    /** Client 生命周期历史，按版本排序。 */
    public List<Map<String, Object>> clientHistory(long clientId) {
        return jdbc.queryForList("SELECT v.version_no,v.operation_type,v.create_by,r.current_version FROM t_business_change_version v "
            + "JOIN t_business_change_record r ON r.history_id=v.history_id WHERE r.record_type='CLIENT' AND r.record_id=? "
            + "ORDER BY v.version_no", Long.toString(clientId));
    }

    /** 某版生命周期历史的 change_version_id；不存在为 -1。 */
    public long clientChangeId(long clientId, int version) {
        var ids = jdbc.queryForList("SELECT v.change_version_id FROM t_business_change_version v JOIN t_business_change_record r "
            + "ON r.history_id=v.history_id WHERE r.record_type='CLIENT' AND r.record_id=? AND v.version_no=?", Long.class,
            Long.toString(clientId), version);
        return ids.isEmpty() ? -1 : ids.getFirst();
    }

    /** 经营资料分类归属。 */
    public Map<String, Object> classificationProfile(long clientId) {
        return jdbc.queryForMap("SELECT classification_case_id,classification_confirmed_by,classification_confirmed_time "
            + "FROM t_client_business_profile WHERE client_id=?", clientId);
    }

    /** 经营资料的分类行。 */
    public List<Map<String, Object>> classifications(long clientId) {
        return jdbc.queryForList("SELECT c.industry_code,c.mcc_code,c.primary_slot FROM t_client_business_classification c "
            + "JOIN t_client_business_profile p ON p.business_profile_id=c.business_profile_id WHERE p.client_id=? ORDER BY c.industry_code",
            clientId);
    }

    /** 某类补偿任务。 */
    public List<Map<String, Object>> events(String eventType, String eventKey) {
        return jdbc.queryForList("SELECT event_key,execute_status,execute_count,last_error_code FROM t_compensation_event "
            + "WHERE event_type=? AND event_key=?", eventType, eventKey);
    }

    /** 申请提交相关列。 */
    public Map<String, Object> application(long applicationId) {
        return jdbc.queryForMap("SELECT status,current_submission_id,due_diligence_case_id,submitted_time,active_slot "
            + "FROM t_client_application WHERE application_id=?", applicationId);
    }

    /** 资料证件绑定。 */
    public Map<String, Object> identifier(long profileId) {
        return jdbc.queryForMap("SELECT identity_key_id,issuer_scope_key FROM t_party_profile_identifier WHERE profile_id=?", profileId);
    }

    /** 某主体的身份键。 */
    public List<Map<String, Object>> identityKeys(long partyId) {
        return jdbc.queryForList("SELECT identity_key_id,identifier_type,issuing_country,issuer_scope_key,normalized_identifier_value "
            + "FROM t_party_identity_key WHERE party_id=?", partyId);
    }

    /** 审核登记补偿任务。 */
    public List<Map<String, Object>> reviewEvents(String submissionId) {
        return jdbc.queryForList("SELECT event_key,execute_status,execute_count,last_error_code FROM t_compensation_event "
            + "WHERE event_type='CLIENT_REVIEW_REGISTRATION' AND event_key=?", submissionId);
    }

    /** 当前所有入驻审核相关补偿任务数。 */
    public int reviewEventCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_compensation_event WHERE event_type IN ('CLIENT_REVIEW_REGISTRATION',"
            + "'CLIENT_REVIEW_CLASSIFICATION','CLIENT_TENANT_PROJECTION')", Integer.class);
    }

    /** 按测试登记的标识删除全部夹具及系统生成的身份键、审核登记任务。 */
    public void delete(Collection<Long> applicationIds, Collection<Long> partyIds, Collection<Long> clientIds) {
        String apps = in(applicationIds), parties = in(partyIds), clients = in(clientIds);
        jdbc.update("DELETE FROM t_compensation_event WHERE event_type IN ('CLIENT_REVIEW_REGISTRATION','CLIENT_REVIEW_CLASSIFICATION',"
            + "'CLIENT_TENANT_PROJECTION')");
        String recordIds = clientIds.isEmpty() ? "('-1')"
            : clientIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(",", "(", ")"));
        jdbc.update("DELETE v FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id=v.history_id "
            + "WHERE r.record_type='CLIENT' AND r.record_id IN " + recordIds);
        jdbc.update("DELETE FROM t_business_change_record WHERE record_type='CLIENT' AND record_id IN " + recordIds);
        jdbc.update("DELETE c FROM t_client_business_classification c JOIN t_client_business_profile p "
            + "ON p.business_profile_id=c.business_profile_id WHERE p.client_id IN " + clients);
        jdbc.update("DELETE FROM t_application_document WHERE client_application_id IN " + apps);
        jdbc.update("DELETE FROM t_party_relation WHERE client_application_id IN " + apps);
        jdbc.update("DELETE FROM t_client_application WHERE application_id IN " + apps);
        jdbc.update("DELETE FROM t_client_business_profile WHERE client_id IN " + clients);
        jdbc.update("DELETE FROM t_client WHERE client_id IN " + clients);
        jdbc.update("DELETE FROM t_party_identity_key WHERE party_id IN " + parties);
        String profiles = "(SELECT profile_id FROM (SELECT profile_id FROM t_party_profile WHERE party_id IN " + parties + ") p)";
        for (String table : List.of("t_party_contact", "t_party_profile_identifier", "t_party_address", "t_individual_profile",
            "t_organization_profile")) {
            jdbc.update("DELETE FROM " + table + " WHERE profile_id IN " + profiles);
        }
        jdbc.update("DELETE FROM t_party_profile WHERE party_id IN " + parties);
        jdbc.update("DELETE FROM t_party WHERE party_id IN " + parties);
    }

    /** 资料当前由哪份申请编辑。 */
    public Long draftApplication(long profileId) {
        return jdbc.queryForObject("SELECT draft_application_id FROM t_party_profile WHERE profile_id=?", Long.class, profileId);
    }

    /** 关系当前所属申请。 */
    public Long relationApplication(long relationId) {
        return jdbc.queryForObject("SELECT client_application_id FROM t_party_relation WHERE relation_id=?", Long.class, relationId);
    }

    /** 申请下的材料引用数量。 */
    public int documentCount(long applicationId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_application_document WHERE client_application_id=?", Integer.class,
            applicationId);
    }

    /** 已插入夹具数量，用于检查空基线。 */
    public int count(Collection<Long> partyIds) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_party WHERE party_id IN " + in(partyIds), Integer.class);
    }

    /** 数值标识的 IN 列表；空集合使用不可能的 -1。 */
    static String in(Collection<Long> ids) {
        return ids.isEmpty() ? "(-1)" : ids.stream().map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
    }
}
