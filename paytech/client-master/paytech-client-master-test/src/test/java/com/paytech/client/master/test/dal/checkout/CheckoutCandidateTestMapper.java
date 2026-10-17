package com.paytech.client.master.test.dal.checkout;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
