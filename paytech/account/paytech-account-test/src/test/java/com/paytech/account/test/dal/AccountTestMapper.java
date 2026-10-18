package com.paytech.account.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Account 测试专用读写：只读断言、篡改构造与全局清理；只触碰 mor_account 的 8 张表。 */
@Mapper
public interface AccountTestMapper {

    /** 统计 mor_account 全部 8 张表的行数，按表返回。 */
    @Select("""
        SELECT 't_account_balance' t, COUNT(*) c FROM t_account_balance UNION ALL
        SELECT 't_balance_applied_fact', COUNT(*) FROM t_balance_applied_fact UNION ALL
        SELECT 't_balance_applied_entry', COUNT(*) FROM t_balance_applied_entry UNION ALL
        SELECT 't_balance_movement', COUNT(*) FROM t_balance_movement UNION ALL
        SELECT 't_balance_discrepancy', COUNT(*) FROM t_balance_discrepancy UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event
        """)
    List<Map<String, Object>> countRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 读取一个余额，键为 account_code、owner_type、owner_id、balance_bucket。 */
    @Select("SELECT * FROM t_account_balance WHERE book_entity_type = 'OPERATING_ENTITY' AND book_entity_id = #{entityNo} "
        + "AND currency = #{currency} AND account_code = #{accountCode} AND owner_type = #{ownerType} "
        + "AND owner_id = #{ownerId} AND balance_bucket = #{bucket}")
    Map<String, Object> selectBalance(@Param("entityNo") String entityNo, @Param("currency") String currency,
        @Param("accountCode") String accountCode, @Param("ownerType") String ownerType, @Param("ownerId") String ownerId,
        @Param("bucket") String bucket);

    /** 读取全部余额，按科目代码、核算对象、余额桶排序。 */
    @Select("SELECT * FROM t_account_balance ORDER BY account_code, owner_type, owner_id, balance_bucket")
    List<Map<String, Object>> selectBalances();

    /** 读取一条经济事实的处理记录。 */
    @Select("SELECT * FROM t_balance_applied_fact WHERE fact_id = #{factId}")
    Map<String, Object> selectAppliedFact(@Param("factId") long factId);

    /** 统计一条已处理事实的余额变化行。 */
    @Select("SELECT COUNT(*) FROM t_balance_applied_entry WHERE fact_id = #{factId}")
    int countEntries(@Param("factId") long factId);

    /** 统计已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);

    /** 统计全部已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event")
    int countAllConsumedEvents();

    /** 读取全部日终核对差异，按科目代码、核算对象排序。 */
    @Select("SELECT * FROM t_balance_discrepancy ORDER BY account_code, owner_type, owner_id, balance_bucket")
    List<Map<String, Object>> selectDiscrepancies();

    /** 直接篡改一个余额的净额，构造“账户余额与 ledger 分录不一致”的核对场景。 */
    @Update("UPDATE t_account_balance SET net_balance = #{netBalance} WHERE account_code = #{accountCode} "
        + "AND owner_type = #{ownerType} AND owner_id = #{ownerId}")
    void updateNetBalance(@Param("accountCode") String accountCode, @Param("ownerType") String ownerType,
        @Param("ownerId") String ownerId, @Param("netBalance") String netBalance);
}
