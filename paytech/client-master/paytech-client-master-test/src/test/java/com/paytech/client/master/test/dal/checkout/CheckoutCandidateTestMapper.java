package com.paytech.client.master.test.dal.checkout;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paytech.client.master.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.client.master.domain.enums.EntitySourceRelationClientSourceBasis;
import com.paytech.client.master.domain.enums.EntitySourceRelationSourceType;
import com.paytech.client.master.domain.enums.EntitySourceRelationStatus;
import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.test.mocker.checkout.CheckoutCandidateFixture;

/** 只修改本例候选夹具的合成主档行，制造候选负例；自原 CCT CheckoutTestMapper 搬入。 */
@TestComponent
public class CheckoutCandidateTestMapper {
    private final JdbcTemplate jdbc;

    /** 使用被测应用的同一数据源。 */
    public CheckoutCandidateTestMapper(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /** 改变当前 Client 主体批准时间，null 表示仍是草稿。 */
    public void clientApproval(Instant at) {
        jdbc.update("UPDATE t_party_profile SET approved_time=? WHERE party_id=(SELECT party_id FROM t_client WHERE client_id=?)",
            at == null ? null : Timestamp.from(at), CheckoutCandidateFixture.CLIENT_ID);
    }

    /** 结束夹具中的两个准确 Scope。 */
    public void endScopes() {
        jdbc.update("UPDATE t_operating_entity_scope SET status='ENDED',active_slot=NULL,effective_to='2026-09-10' "
            + "WHERE scope_id IN (?,?)", 910000000000105L, 910000000000205L);
    }

    /** 结束 AE991 的来源关系。 */
    public void endFirstSource() {
        jdbc.update("UPDATE t_entity_source_relation SET status='ENDED',active_source_slot=NULL,effective_to='2026-09-10' "
            + "WHERE source_relation_id=?", 910000000000103L);
    }

    /** 设置或清除当前分类的 Primary 槽位。 */
    public void primaryClassification(Integer slot) {
        jdbc.update("UPDATE t_client_business_classification SET primary_slot=? WHERE classification_id=?", slot,
            910000000000004L);
    }

    /** 设置或清除夹具 Client 经营资料的分类确认时间；null 表示未确认。 */
    public void classificationConfirmedTime(Instant at) {
        jdbc.update("UPDATE t_client_business_profile SET classification_confirmed_time=? WHERE client_id=?",
            at == null ? null : Timestamp.from(at), CheckoutCandidateFixture.CLIENT_ID);
    }

    /** 删除夹具 Client 的经营资料及其分类，制造没有经营资料的 Client。 */
    public void removeBusinessProfile() {
        jdbc.update("DELETE FROM t_client_business_classification WHERE business_profile_id IN "
            + "(SELECT business_profile_id FROM t_client_business_profile WHERE client_id=?)", CheckoutCandidateFixture.CLIENT_ID);
        jdbc.update("DELETE FROM t_client_business_profile WHERE client_id=?", CheckoutCandidateFixture.CLIENT_ID);
    }

    /** 制造 Scope 指向被拒绝申请的完整性冲突。 */
    public void rejectScopeApplication() {
        jdbc.update("UPDATE t_operating_entity_application SET status='REJECTED' WHERE application_id=?", 910000000000104L);
    }

    /** 制造数据库枚举超出当前代码认识范围的兼容失败。 */
    public void unknownClientStatus() {
        jdbc.update("UPDATE t_client SET status='UNKNOWN_STATUS' WHERE client_id=?", CheckoutCandidateFixture.CLIENT_ID);
    }

    /** 推进 OE 根修订。 */
    public void advanceEntityRoot(String entityNo) {
        jdbc.update("UPDATE t_business_change_record SET current_version=current_version+1 "
            + "WHERE record_type='OPERATING_ENTITY' AND record_id=? AND parent_history_id IS NULL", entityNo);
    }

    /** 写入 Client 已存卡开关。 */
    public void storedCardSetting(long clientId, boolean enabled) {
        jdbc.update("INSERT INTO t_client_stored_card_setting (client_id,stored_card_enabled,version,create_time,update_time) "
            + "VALUES (?,?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", clientId, enabled ? 1 : 0);
    }

    /** 把 OE 的来源改回已取消的 PLATFORM（数据库残留值）。 */
    public void platformSource(String entityNo) {
        jdbc.update("UPDATE t_entity_source_relation SET source_type='PLATFORM',source_partner_id=NULL WHERE entity_no=?", entityNo);
    }

    /** 把 OE 的来源改为 CLIENT（同一法律主体）。 */
    public void clientSource(String entityNo, long clientId) {
        jdbc.update("UPDATE t_entity_source_relation SET source_type=?,source_partner_id=NULL,source_client_id=?,client_source_basis=? "
            + "WHERE entity_no=?", EntitySourceRelationSourceType.CLIENT.getCode(), clientId,
            EntitySourceRelationClientSourceBasis.SAME_PARTY.getCode(), entityNo);
    }

    /** 结束某 Local Partner 的全部 PARTNER 来源关系。 */
    public void endPartnerSources(long localPartnerId) {
        jdbc.update("UPDATE t_entity_source_relation SET status=?,active_source_slot=NULL,effective_to='2026-09-10' "
            + "WHERE source_partner_id=?", EntitySourceRelationStatus.ENDED.getCode(), localPartnerId);
    }

    /** 删除一个业务根，模拟历史手工数据没有根。 */
    public void deleteRoot(BusinessChangeRecordRecordType type, String recordId) {
        jdbc.update("DELETE FROM t_business_change_record WHERE record_type=? AND record_id=?", type.getCode(), recordId);
    }

    /** 业务根当前修订；不存在时为 null。 */
    public Long rootVersion(BusinessChangeRecordRecordType type, String recordId) {
        return jdbc.queryForList("SELECT current_version FROM t_business_change_record WHERE record_type=? AND record_id=? "
            + "AND parent_history_id IS NULL", Long.class, type.getCode(), recordId).stream().findFirst().orElse(null);
    }

    /** 业务根的全部快照，按版本升序。 */
    public List<Map<String, Object>> snapshots(BusinessChangeRecordRecordType type, String recordId) {
        return jdbc.queryForList("SELECT v.version_no,v.operation_type,v.snapshot_format,v.snapshot_json,v.create_by "
            + "FROM t_business_change_version v JOIN t_business_change_record r ON r.history_id=v.history_id "
            + "WHERE r.record_type=? AND r.record_id=? ORDER BY v.version_no", type.getCode(), recordId);
    }

    /** OE 当前收单资质。 */
    public String acquiringQualification(String entityNo) {
        return jdbc.queryForObject("SELECT acquiring_qualification FROM t_operating_entity WHERE entity_no=?", String.class, entityNo);
    }

    /** 直接改写 OE 收单资质。 */
    public void acquiringQualification(String entityNo, String qualification) {
        jdbc.update("UPDATE t_operating_entity SET acquiring_qualification=? WHERE entity_no=?", qualification, entityNo);
    }

    /** 某 Local Partner 的全部角色行，按角色 Code 与 ID 升序。 */
    public List<Map<String, Object>> partnerRoles(long localPartnerId) {
        return jdbc.queryForList("SELECT role_code,status,active_slot+0 AS active_slot FROM t_partner_role WHERE local_partner_id=? "
            + "ORDER BY role_code,partner_role_id", localPartnerId);
    }

    /** 某角色与主体的全部统一角色索引行，按 ID 升序。 */
    public List<Map<String, Object>> partyRoles(PartyRoleCode roleCode, String subjectId) {
        return jdbc.queryForList("SELECT party_id,status,active_slot+0 AS active_slot,effective_to FROM t_party_role "
            + "WHERE role_code=? AND subject_id=? ORDER BY party_role_id", roleCode.getCode(), subjectId);
    }

    /** Client 的法律主体 Party ID。 */
    public long clientPartyId(long clientId) {
        return jdbc.queryForObject("SELECT party_id FROM t_client WHERE client_id=?", Long.class, clientId);
    }
}
