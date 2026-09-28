package com.paytech.client.contract.test.dal.checkout;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.paytech.client.contract.test.mocker.local.checkout.CheckoutFixture;

/** 仅操作本 Checkout 夹具的合成行；没有 DDL 或共享表清理。 */
@TestComponent
public class CheckoutTestMapper {
    private final DataSource source;

    /** 测试数据变更必须使用明确的本地连接，不接受未装配的数据源。 */
    public CheckoutTestMapper(DataSource source) {
        this.source = java.util.Objects.requireNonNull(source);
    }

    /** 改变当前 Client 主体批准时间，null 表示仍是草稿。 */
    public void clientApproval(Instant at) {
        update("UPDATE t_party_profile SET approved_time=? WHERE party_id="
                + "(SELECT party_id FROM t_client WHERE client_id=?)", at == null ? null : Timestamp.from(at),
                CheckoutFixture.CLIENT_ID);
    }

    /** 删除当前 Client 分类确认人，不能以主档 ACTIVE 代替批准。 */
    public void clearClassificationConfirmation() {
        update("UPDATE t_client_business_profile SET classification_confirmed_by=NULL WHERE client_id=?",
                CheckoutFixture.CLIENT_ID);
    }

    /** 结束夹具中的两个准确 Scope。 */
    public void endScopes() {
        update("UPDATE t_operating_entity_scope SET status='ENDED',active_slot=NULL,effective_to='2026-09-10' WHERE scope_id IN (?,?)",
                910000000000105L, 910000000000205L);
    }

    /** 设置或清除当前分类的 Primary 槽位。 */
    public void primaryClassification(Integer slot) {
        update("UPDATE t_client_business_classification SET primary_slot=? WHERE classification_id=?", slot,
                910000000000004L);
    }

    /** 制造 Scope 指向被拒绝申请的完整性冲突。 */
    public void rejectScopeApplication() {
        update("UPDATE t_operating_entity_application SET status='REJECTED' WHERE application_id=?", 910000000000104L);
    }

    /** 制造数据库枚举超出当前代码认识范围的兼容失败。 */
    public void unknownClientStatus() {
        update("UPDATE t_client SET status='UNKNOWN_STATUS' WHERE client_id=?", CheckoutFixture.CLIENT_ID);
    }

    /** Fixture DML 使用独立非只读连接，不改变正式 Service 的事务属性。 */
    private void update(String sql, Object... parameters) {
        try (var connection = source.getConnection()) {
            connection.setReadOnly(false);
            new JdbcTemplate(new SingleConnectionDataSource(connection, true)).update(sql, parameters);
        } catch (java.sql.SQLException exception) {
            throw new AssertionError("Checkout 合成场景准备失败", exception);
        }
    }
}
