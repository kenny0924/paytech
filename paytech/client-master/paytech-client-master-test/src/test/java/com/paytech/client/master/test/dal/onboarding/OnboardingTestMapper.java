package com.paytech.client.master.test.dal.onboarding;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;
import com.paytech.client.master.test.mocker.onboarding.VaultMocker;

/** 入驻测试夹具的插入、只读断言与按标识清理；只操作本测试登记的行。 */
@TestComponent
public class OnboardingTestMapper {
    private final JdbcTemplate jdbc;

    /** 使用 CCT 正式数据源。 */
    public OnboardingTestMapper(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 插入 AE 包下完整的主体：资料头、个人或组织明细、地址、证件与个人扩展属性。 */
    public void insertSubject(long partyId, long profileId, Long draftApplicationId, boolean organization, String addressType,
        LongSupplier ids, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party(party_id,party_no,party_type,create_time,update_time) VALUES(?,?,?,?,?)", partyId,
            "TP" + partyId, organization ? "ORGANIZATION" : "INDIVIDUAL", at, at);
        jdbc.update("INSERT INTO t_party_profile(profile_id,party_id,draft_application_id,jurisdiction_code,current_slot,create_time,"
            + "update_time) VALUES(?,?,?,?,1,?,?)", profileId, partyId, draftApplicationId, OnboardingFixtures.JURISDICTION, at, at);
        if (organization) {
            jdbc.update("INSERT INTO t_organization_profile(profile_id,legal_name,trade_name,incorporation_country,organization_type,"
                + "incorporation_date,registration_authority_code,license_expiry_date,listed_company,create_time,update_time) "
                + "VALUES(?,?,?,?,?,?,?,?,0,?,?)", profileId, "Fixture Trading LLC", "Fixture", "AE", OnboardingFixtures.ORGANIZATION_TYPE,
                Date.valueOf("2020-01-15"), OnboardingFixtures.REGISTRATION_AUTHORITY, Date.valueOf("2031-12-31"), at, at);
        } else {
            jdbc.update("INSERT INTO t_individual_profile(profile_id,given_name,family_name,birth_date,nationality_country,"
                + "residence_country,birth_place,birth_country,gender,source_of_funds_code,pep_self_declared,create_time,update_time) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?)", profileId, "Alex", "Fixture", Date.valueOf("1990-05-01"), "GB", "AE", "London",
                "GB", "MALE", "SALARY", at, at);
            insertAttribute(ids.getAsLong(), profileId, "OCCUPATION", "EMPLOYEE", now);
            insertAttribute(ids.getAsLong(), profileId, "TAX_RESIDENCE_COUNTRY", "AE", now);
        }
        jdbc.update("INSERT INTO t_party_address(address_id,profile_id,address_type,address_line1,city,state_or_province,country_code,"
            + "address_format_code,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)", ids.getAsLong(), profileId, addressType,
            "1 Fixture Road", "Dubai", "AE-DU", "AE", "AE_POBOX", at, at);
        if (organization) {
            String license = OnboardingFixtures.tradeLicense(partyId);
            jdbc.update("INSERT INTO t_party_profile_identifier(profile_identifier_id,profile_id,identifier_type,issuing_country,"
                + "public_identifier_value,identifier_masked_value,create_time,update_time) VALUES(?,?,?,?,?,?,?,?)", ids.getAsLong(),
                profileId, OnboardingFixtures.TRADE_LICENSE, "AE", license, OnboardingFixtures.masked(license), at, at);
        } else {
            String passport = OnboardingFixtures.passport(partyId);
            jdbc.update("INSERT INTO t_party_profile_identifier(profile_identifier_id,profile_id,identifier_type,issuing_country,"
                + "identifier_vault_token,identifier_masked_value,expiry_date,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)",
                ids.getAsLong(), profileId, OnboardingFixtures.PASSPORT, OnboardingFixtures.PASSPORT_COUNTRY,
                OnboardingFixtures.passportToken(passport), OnboardingFixtures.masked(passport), Date.valueOf("2031-01-01"), at, at);
        }
    }

    /** 插入一项文本扩展属性。 */
    public void insertAttribute(long attributeId, long profileId, String code, String value, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party_attribute(attribute_id,profile_id,attribute_code,value_text,create_time,update_time) "
            + "VALUES(?,?,?,?,?,?)", attributeId, profileId, code, value, at, at);
    }

    /** 插入 AE 包下的 Client、经营资料、申请与联系方式。 */
    public void insertApplication(long applicationId, long clientId, long partyId, long profileId, long businessProfileId,
        long accountId, boolean organization, long contactId, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_client(client_id,client_no,party_id,status,jurisdiction_code,create_time,update_time) "
            + "VALUES(?,?,?,'PENDING',?,?,?)", clientId, "TC" + clientId, partyId, OnboardingFixtures.JURISDICTION, at, at);
        jdbc.update("INSERT INTO t_client_business_profile(business_profile_id,client_id,business_model_code,website_url,"
            + "expected_volume,expected_ticket,currency,business_description,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?)",
            businessProfileId, clientId, "SERVICES", "https://fixture.example.test", new BigDecimal("50000"), new BigDecimal("25.5"),
            "USD", "Fixture consulting services", at, at);
        jdbc.update("INSERT INTO t_client_application(application_id,application_no,client_id,requested_by_account_id,status,"
            + "active_slot,qualification_type,application_type,jurisdiction_code,organization_type_code,schema_id,submission_round,"
            + "create_time,update_time) VALUES(?,?,?,?,'DRAFT',1,?,'ONBOARDING',?,?,?,1,?,?)", applicationId, "TA" + applicationId,
            clientId, accountId, organization ? "ORGANIZATION_KYB" : "INDIVIDUAL_KYC", OnboardingFixtures.JURISDICTION,
            organization ? OnboardingFixtures.ORGANIZATION_TYPE : null,
            organization ? OnboardingFixtures.AE_ORGANIZATION_SCHEMA : OnboardingFixtures.AE_INDIVIDUAL_SCHEMA, at, at);
        jdbc.update("INSERT INTO t_party_contact(contact_id,profile_id,contact_type,contact_vault_token,contact_masked_value,"
            + "create_time,update_time) VALUES(?,?,'EMAIL',?,?,?,?)", contactId, profileId,
            VaultMocker.token("EMAIL_ADDRESS_V1", "fixture+" + contactId + "@example.test"), "f***@example.test", at, at);
        jdbc.update("INSERT INTO t_party_contact(contact_id,profile_id,contact_type,contact_vault_token,contact_masked_value,"
            + "create_time,update_time) VALUES(?,?,'PHONE',?,?,?,?)", contactId + 1, profileId,
            VaultMocker.token("PHONE_E164_V1", "+97150" + contactId % 10_000_000), "+971****0000", at, at);
    }

    /** 改写夹具申请冻结的 Schema（如 v2 在途申请、v3 新申请）。 */
    public void freezeSchema(long applicationId, long schemaId) {
        jdbc.update("UPDATE t_client_application SET schema_id=? WHERE application_id=?", schemaId, applicationId);
    }

    /** 直接写入经营资料的业务信息补采四列（夹具；不经草稿入口）。 */
    public void businessInfo(long clientId, String paymentMethods, String refundPolicyUrl, String fulfillmentType, Integer fulfillmentDays) {
        jdbc.update("UPDATE t_client_business_profile SET expected_payment_methods=?,refund_policy_url=?,fulfillment_type=?,"
            + "fulfillment_days=? WHERE client_id=?", paymentMethods, refundPolicyUrl, fulfillmentType, fulfillmentDays, clientId);
    }

    /** 经营资料的业务信息补采四列。 */
    public Map<String, Object> businessInfo(long clientId) {
        return jdbc.queryForMap("SELECT expected_payment_methods,refund_policy_url,fulfillment_type,fulfillment_days "
            + "FROM t_client_business_profile WHERE client_id=?", clientId);
    }

    /** 申请的 Client。 */
    public long applicationClient(long applicationId) {
        return jdbc.queryForObject("SELECT client_id FROM t_client_application WHERE application_id=?", Long.class, applicationId);
    }

    /** 插入申请声明（TRUE）。 */
    public void insertDeclarations(long applicationId, List<String> types, long accountId, LongSupplier ids, Instant now) {
        var at = Timestamp.from(now);
        for (String type : types) {
            jdbc.update("INSERT INTO t_application_declaration(declaration_id,client_application_id,declaration_type,declared_value,"
                + "declared_by_account_id,declared_time,create_time) VALUES(?,?,?,'TRUE',?,?,?)", ids.getAsLong(), applicationId, type,
                accountId, at, at);
        }
    }

    /** 插入组织与人员的草稿关系；计入受益人的关系填 40% 持股。 */
    public void insertRelation(long relationId, long applicationId, long organizationPartyId, long personPartyId, String type,
        Instant now) {
        var at = Timestamp.from(now);
        boolean owner = "UBO".equals(type) || "OWNERSHIP".equals(type);
        jdbc.update("INSERT INTO t_party_relation(relation_id,organization_party_id,related_party_id,relation_type,"
            + "ownership_percentage,effective_from,status,client_application_id,create_time,update_time) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?)",
            relationId, organizationPartyId, personPartyId, type, owner ? new BigDecimal("40") : null,
            Timestamp.from(Instant.parse("2024-01-01T00:00:00Z")), applicationId, at, at);
    }

    /** 插入材料引用。 */
    public void insertDocument(long documentId, long applicationId, long subjectPartyId, String requirementCode, String documentType,
        String fileAddress, String contentHash, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_application_document(document_id,client_application_id,subject_party_id,requirement_code,"
            + "document_type,file_address,content_hash,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?)", documentId, applicationId,
            subjectPartyId, requirementCode, documentType, fileAddress, contentHash, at, at);
    }

    /** 插入属于某主体的永久身份键；scope 为 GLOBAL、签发国或登记机关。 */
    public void insertIdentityKey(long identityKeyId, long partyId, String type, String scope, String keyValue, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party_identity_key(identity_key_id,party_id,identifier_type,issuing_country,issuer_scope_key,"
            + "identifier_key_value,create_time,update_time) VALUES(?,?,?,?,?,?,?,?)", identityKeyId, partyId, type,
            scope.matches("[A-Z]{2}") ? scope : null, scope, keyValue, at, at);
    }

    /** 清空某张资料表的一列，用于构造缺项。 */
    public void clear(String table, String column, long profileId) {
        if (!table.matches("t_[a-z_]+") || !column.matches("[a-z_0-9]+")) {
            throw new IllegalArgumentException("unsafe identifier");
        }
        jdbc.update("UPDATE " + table + " SET " + column + "=NULL WHERE profile_id=?", profileId);
    }

    /** 设置某张资料表的一列，用于构造违规取值。 */
    public void set(String table, String column, Object value, long profileId) {
        if (!table.matches("t_[a-z_]+") || !column.matches("[a-z_0-9]+")) {
            throw new IllegalArgumentException("unsafe identifier");
        }
        jdbc.update("UPDATE " + table + " SET " + column + "=? WHERE profile_id=?", value, profileId);
    }

    /** 删除申请的某类声明。 */
    public void deleteDeclaration(long applicationId, String type) {
        jdbc.update("DELETE FROM t_application_declaration WHERE client_application_id=? AND declaration_type=?", applicationId, type);
    }

    /** 申请当前资料版本。 */
    public long draftRevision(long applicationId) {
        return jdbc.queryForObject("SELECT draft_revision FROM t_client_application WHERE application_id=?", Long.class, applicationId);
    }

    /** 申请的全部声明行（类型、条款版本、资料版本），按追加顺序。 */
    public List<Map<String, Object>> declarations(long applicationId) {
        return jdbc.queryForList("SELECT declaration_type,declared_value,terms_version,draft_revision FROM t_application_declaration "
            + "WHERE client_application_id=? ORDER BY declared_time,declaration_id", applicationId);
    }

    /** 设置关系的持股比例。 */
    public void ownership(long relationId, java.math.BigDecimal percentage) {
        jdbc.update("UPDATE t_party_relation SET ownership_percentage=? WHERE relation_id=?", percentage, relationId);
    }

    /** 把申请置为已登记审核：UNDER_REVIEW、当前提交与主审核任务。 */
    public void underReview(long applicationId, String submissionId, long caseId, Instant now) {
        jdbc.update("UPDATE t_client_application SET status='UNDER_REVIEW', current_submission_id=?, due_diligence_case_id=?, "
            + "submitted_time=?, update_time=? WHERE application_id=?", submissionId, caseId, Timestamp.from(now), Timestamp.from(now),
            applicationId);
    }

    /** 申请置为已批准并释放名额，Client 激活；与审核结果落地后的事实一致。 */
    public void approve(long applicationId, long clientId, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("UPDATE t_client_application SET status='APPROVED', active_slot=NULL, update_time=? WHERE application_id=?", at,
            applicationId);
        jdbc.update("UPDATE t_client SET status='ACTIVE', update_time=? WHERE client_id=?", at, clientId);
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

    /** Client 主体资料的首次批准时间；未批准为 null。 */
    public Instant profileApprovedTime(long clientId) {
        Timestamp value = jdbc.queryForObject("SELECT f.approved_time FROM t_party_profile f JOIN t_client c ON c.party_id=f.party_id "
            + "WHERE c.client_id=?", Timestamp.class, clientId);
        return value == null ? null : value.toInstant();
    }

    /** 设置 Client 主体资料的批准时间（模拟已写入批准时间的数据）。 */
    public void setProfileApprovedTime(long clientId, Instant time) {
        jdbc.update("UPDATE t_party_profile f JOIN t_client c ON c.party_id=f.party_id SET f.approved_time=? WHERE c.client_id=?",
            Timestamp.from(time), clientId);
    }

    /** 申请最后更新时间。 */
    public Instant applicationUpdateTime(long applicationId) {
        return jdbc.queryForObject("SELECT update_time FROM t_client_application WHERE application_id=?", Timestamp.class, applicationId)
            .toInstant();
    }

    /** 把该 Client 全部申请的最后更新时间改为指定值（模拟批准后申请记录再被更新）。 */
    public void touchApplications(long clientId, Instant time) {
        jdbc.update("UPDATE t_client_application SET update_time=? WHERE client_id=?", Timestamp.from(time), clientId);
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

    /** 把某 Client 经营资料的全部分类行改为指定行业（模拟迁移前保存的一级行业）。 */
    public void setClassificationIndustry(long clientId, String industryCode) {
        jdbc.update("UPDATE t_client_business_classification c JOIN t_client_business_profile p "
            + "ON p.business_profile_id=c.business_profile_id SET c.industry_code=? WHERE p.client_id=?", industryCode, clientId);
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
        return jdbc.queryForMap("SELECT identity_key_id,issuer_scope_key,identifier_type,"
            + "identifier_vault_token,public_identifier_value,identifier_masked_value,identifier_check_result,expiry_date "
            + "FROM t_party_profile_identifier WHERE profile_id=?", profileId);
    }

    /** 某主体的身份键。 */
    public List<Map<String, Object>> identityKeys(long partyId) {
        return jdbc.queryForList("SELECT identity_key_id,identifier_type,issuing_country,issuer_scope_key,identifier_key_value "
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
        jdbc.update("DELETE FROM t_application_declaration WHERE client_application_id IN " + apps);
        jdbc.update("DELETE FROM t_party_relation WHERE client_application_id IN " + apps);
        jdbc.update("DELETE FROM t_client_application WHERE application_id IN " + apps);
        jdbc.update("DELETE FROM t_client_business_profile WHERE client_id IN " + clients);
        jdbc.update("DELETE FROM t_client WHERE client_id IN " + clients);
        jdbc.update("DELETE FROM t_party_identity_key WHERE party_id IN " + parties);
        String profiles = "(SELECT profile_id FROM (SELECT profile_id FROM t_party_profile WHERE party_id IN " + parties + ") p)";
        for (String table : List.of("t_party_attribute", "t_party_contact", "t_party_profile_identifier", "t_party_address", "t_individual_profile",
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

    /** 申请下各材料引用保存的文件地址与摘要，按材料 ID 排序。 */
    public List<Map<String, Object>> documents(long applicationId) {
        return jdbc.queryForList("SELECT document_id,file_address,content_hash FROM t_application_document WHERE client_application_id=? "
            + "ORDER BY document_id", applicationId);
    }

    /** 已插入夹具数量，用于检查空基线。 */
    public int count(Collection<Long> partyIds) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_party WHERE party_id IN " + in(partyIds), Integer.class);
    }

    /** 申请主主体当前资料 ID（Client 的 Party 的当前资料）。 */
    public long applicationProfile(long applicationId) {
        return jdbc.queryForObject("SELECT f.profile_id FROM t_client_application a JOIN t_client c ON c.client_id=a.client_id "
            + "JOIN t_party_profile f ON f.party_id=c.party_id AND f.current_slot=1 WHERE a.application_id=?", Long.class, applicationId);
    }

    /** 申请主主体当前资料的批准相关列。 */
    public Map<String, Object> applicationProfileState(long applicationId) {
        return jdbc.queryForMap("SELECT f.content_hash,f.draft_application_id,f.current_slot,f.approved_time,f.jurisdiction_code "
            + "FROM t_client_application a JOIN t_client c ON c.client_id=a.client_id JOIN t_party_profile f ON f.party_id=c.party_id "
            + "AND f.current_slot=1 WHERE a.application_id=?", applicationId);
    }

    /** 申请的辖区、责任方、入驻码与退回期限。 */
    public Map<String, Object> applicationTerms(long applicationId) {
        return jdbc.queryForMap("SELECT jurisdiction_code,organization_type_code,schema_id,onboarding_code_id,responsible_partner_id,"
            + "submission_round,previous_case_id,review_due_time,status,active_slot FROM t_client_application WHERE application_id=?",
            applicationId);
    }

    /** Client 的辖区与责任方。 */
    public Map<String, Object> clientTerms(long clientId) {
        return jdbc.queryForMap("SELECT jurisdiction_code,responsible_partner_id,status FROM t_client WHERE client_id=?", clientId);
    }

    /** 插入 ACTIVE 的 Local Partner；roleCode 为空时不具备商业角色。 */
    public void insertLocalPartner(long localPartnerId, long partyId, String roleCode, long roleId, Instant now) {
        var at = Timestamp.from(now);
        jdbc.update("INSERT INTO t_party(party_id,party_no,party_type,create_time,update_time) VALUES(?,?,'ORGANIZATION',?,?)", partyId,
            "TP" + partyId, at, at);
        jdbc.update("INSERT INTO t_party_profile(profile_id,party_id,create_time,update_time) VALUES(?,?,?,?)", partyId + 1, partyId, at, at);
        jdbc.update("INSERT INTO t_organization_profile(profile_id,legal_name,create_time,update_time) VALUES(?,?,?,?)", partyId + 1,
            "Gulf Pay LLC", at, at);
        jdbc.update("INSERT INTO t_local_partner(local_partner_id,partner_no,party_id,status,activation_time,create_time,update_time) "
            + "VALUES(?,?,?,'ACTIVE',?,?,?)", localPartnerId, "LP" + localPartnerId, partyId, at, at, at);
        if (roleCode != null) {
            jdbc.update("INSERT INTO t_partner_role(partner_role_id,local_partner_id,role_code,status,active_slot,create_time,update_time) "
                + "VALUES(?,?,?,'ACTIVE',1,?,?)", roleId, localPartnerId, roleCode, at, at);
        }
    }

    /**
     * 把夹具改成 2026-10-27 之前的遗留形状（发布回填之后）：申请辖区 LEGACY、无 Schema 与组织类型，Client 与资料无辖区，
     * 证件号已脱敏（身份键 LEGACY-REDACTED-<ID>，公开号为空，只留掩码）
     */
    public void legacyShape(long applicationId, long clientId, long partyId, long profileId) {
        jdbc.update("UPDATE t_client_application SET jurisdiction_code='LEGACY', schema_id=NULL, organization_type_code=NULL "
            + "WHERE application_id=?", applicationId);
        jdbc.update("UPDATE t_client SET jurisdiction_code=NULL WHERE client_id=?", clientId);
        jdbc.update("UPDATE t_party_profile SET jurisdiction_code=NULL WHERE profile_id=?", profileId);
        jdbc.update("UPDATE t_party_profile_identifier SET identifier_vault_token=NULL, public_identifier_value=NULL, "
            + "identifier_masked_value='****1234' WHERE profile_id=?", profileId);
        jdbc.update("UPDATE t_party_identity_key SET identifier_key_value=CONCAT('LEGACY-REDACTED-', identity_key_id) WHERE party_id=?",
            partyId);
    }

    /** 入驻码当前使用次数与状态。 */
    public Map<String, Object> onboardingCode(long codeId) {
        return jdbc.queryForMap("SELECT code,status,use_count,max_uses,local_partner_id FROM t_partner_onboarding_code WHERE code_id=?",
            codeId);
    }

    /** 某份资料联系方式：类型到 Vault Token。 */
    public Map<String, String> contactTokens(long profileId) {
        var result = new java.util.LinkedHashMap<String, String>();
        jdbc.queryForList("SELECT contact_type,contact_vault_token FROM t_party_contact WHERE profile_id=? ORDER BY contact_type",
            profileId).forEach(row -> result.put((String)row.get("contact_type"), (String)row.get("contact_vault_token")));
        return result;
    }

    /** 资料地址行。 */
    public Map<String, Object> address(long profileId) {
        return jdbc.queryForMap("SELECT address_format_code,address_line1,building_number,additional_number,district,city,"
            + "state_or_province,postal_code,po_box,country_code FROM t_party_address WHERE profile_id=?", profileId);
    }

    /** 资料的联系方式类型，按类型排序。 */
    public List<String> contactTypes(long profileId) {
        return jdbc.queryForList("SELECT contact_type FROM t_party_contact WHERE profile_id=? ORDER BY contact_type", String.class,
            profileId);
    }

    /** 某主体的当前资料 ID。 */
    public long currentProfile(long partyId) {
        return jdbc.queryForObject("SELECT profile_id FROM t_party_profile WHERE party_id=? AND current_slot=1", Long.class, partyId);
    }

    /** 某人员的关系，按关系类型排序。 */
    public List<Map<String, Object>> relationsOf(long relatedPartyId) {
        return jdbc.queryForList("SELECT relation_id,relation_type,ownership_percentage,ubo_basis,effective_from,status "
            + "FROM t_party_relation WHERE related_party_id=? ORDER BY relation_type", relatedPartyId);
    }

    /** 数值标识的 IN 列表；空集合使用不可能的 -1。 */
    static String in(Collection<Long> ids) {
        return ids.isEmpty() ? "(-1)" : ids.stream().map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
    }
}
