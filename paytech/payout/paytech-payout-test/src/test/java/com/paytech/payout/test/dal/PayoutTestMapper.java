package com.paytech.payout.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * payout 测试专用读取与全局清理：只触碰 mor_payout 的 3 张表
 * <li>数据准备走生产 Mapper（BeneficiaryAccountMocker）；这里只放基线检查、清理、原始行读取与补偿任务观察。</li>
 */
@Mapper
public interface PayoutTestMapper {

    /** 当前库内的全部表名（Schema 就绪检查）。 */
    @Select("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name")
    List<String> listTables();

    /** 3 张表的行数。 */
    @Select("""
        SELECT 't_beneficiary_account' t, COUNT(*) c FROM t_beneficiary_account UNION ALL
        SELECT 't_beneficiary_account_event', COUNT(*) FROM t_beneficiary_account_event UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event
        """)
    List<Map<String, Object>> countWritableRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 账户行的全部列（原值零泄漏检查）。 */
    @Select("SELECT * FROM t_beneficiary_account ORDER BY id")
    List<Map<String, Object>> selectAccountRows();

    /** 账户数。 */
    @Select("SELECT COUNT(*) FROM t_beneficiary_account")
    long countAccounts();

    /** 事件行的全部列，按账户、序号。 */
    @Select("SELECT * FROM t_beneficiary_account_event ORDER BY beneficiary_account_id, event_no")
    List<Map<String, Object>> selectEventRows();

    /** 某账户的事件动作，按序号。 */
    @Select("SELECT action FROM t_beneficiary_account_event WHERE beneficiary_account_id = #{accountId} ORDER BY event_no")
    List<String> selectEventActions(@Param("accountId") long accountId);

    /** 全部补偿任务的身份、状态与载荷。 */
    @Select("SELECT event_key, event_type, sub_event_type, payload_json, execute_status, execute_count, last_error_code "
        + "FROM t_compensation_event ORDER BY id")
    List<Map<String, Object>> selectCompensationEvents();

    /** 仍在执行或待立即执行的补偿任务数（INIT / PROCESSING）。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE execute_status IN ('INIT', 'PROCESSING')")
    long countInFlightCompensationEvents();

    /** 补偿任务总数。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event")
    long countCompensationEvents();

    /** 让 ERROR 任务立即到期（重试场景，测试侧代替等待退避）。 */
    @Update("UPDATE t_compensation_event SET next_execute_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE execute_status = 'ERROR'")
    int makeErrorEventsDue();

    /** 把账户冷却结束时间改到给定秒数之前（冷却到期场景）。 */
    @Update("UPDATE t_beneficiary_account SET cooling_until = UTC_TIMESTAMP(6) - INTERVAL #{seconds} SECOND WHERE id = #{id}")
    int expireCooling(@Param("id") long id, @Param("seconds") int seconds);
}
