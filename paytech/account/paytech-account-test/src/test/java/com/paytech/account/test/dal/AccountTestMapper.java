package com.paytech.account.test.dal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Account 测试专用读写：只读断言、前置数据插入、篡改构造与全局清理；只触碰 mor_account 的 10 张表。 */
@Mapper
public interface AccountTestMapper {

    /** 统计 mor_account 全部 10 张表的行数，按表返回。 */
    @Select("""
        SELECT 't_owner_account' t, COUNT(*) c FROM t_owner_account UNION ALL
        SELECT 't_owner_account_no_sequence', COUNT(*) FROM t_owner_account_no_sequence UNION ALL
        SELECT 't_account_posting_stream', COUNT(*) FROM t_account_posting_stream UNION ALL
        SELECT 't_account_posting', COUNT(*) FROM t_account_posting UNION ALL
        SELECT 't_account_entry', COUNT(*) FROM t_account_entry UNION ALL
        SELECT 't_balance_applied_fact', COUNT(*) FROM t_balance_applied_fact UNION ALL
        SELECT 't_balance_movement', COUNT(*) FROM t_balance_movement UNION ALL
        SELECT 't_consumed_event', COUNT(*) FROM t_consumed_event UNION ALL
        SELECT 't_consumed_aggregate_version', COUNT(*) FROM t_consumed_aggregate_version UNION ALL
        SELECT 't_compensation_event', COUNT(*) FROM t_compensation_event
        """)
    List<Map<String, Object>> countRows();

    /** 删除一张测试可写表的全部行；表名只来自清理组件的固定清单。 */
    @Delete("DELETE FROM ${table}")
    void deleteAll(@Param("table") String table);

    /** 读取一个 Owner 账户，键为主体、币种、科目代码、核算对象与余额桶。 */
    @Select("SELECT * FROM t_owner_account WHERE entity_type = #{entityType} AND entity_id = #{entityId} "
        + "AND currency = #{currency} AND account_code = #{accountCode} AND owner_type = #{ownerType} "
        + "AND owner_id = #{ownerId} AND balance_bucket = #{bucket}")
    Map<String, Object> selectAccount(@Param("entityType") String entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("accountCode") String accountCode, @Param("ownerType") String ownerType,
        @Param("ownerId") String ownerId, @Param("bucket") String bucket);

    /** 读取全部 Owner 账户，按科目代码、核算对象、余额桶排序。 */
    @Select("SELECT * FROM t_owner_account ORDER BY account_code, owner_type, owner_id, balance_bucket")
    List<Map<String, Object>> selectAccounts();

    /** 读取一条计费事实的处理记录。 */
    @Select("SELECT * FROM t_balance_applied_fact WHERE fact_id = #{factId}")
    Map<String, Object> selectAppliedFact(@Param("factId") long factId);

    /** 读取一条计费事实生成的批次。 */
    @Select("SELECT * FROM t_account_posting WHERE source_type = 'ECONOMIC_FACT' AND fact_id = #{factId}")
    Map<String, Object> selectFactPosting(@Param("factId") long factId);

    /** 读取一个主体与币种下的全部批次，按序号排序。 */
    @Select("SELECT * FROM t_account_posting WHERE entity_type = #{entityType} AND entity_id = #{entityId} "
        + "AND currency = #{currency} ORDER BY posting_sequence")
    List<Map<String, Object>> selectPostings(@Param("entityType") String entityType, @Param("entityId") String entityId,
        @Param("currency") String currency);

    /** 读取一个批次的流水，按行号排序。 */
    @Select("SELECT * FROM t_account_entry WHERE posting_id = #{postingId} ORDER BY line_no")
    List<Map<String, Object>> selectEntries(@Param("postingId") long postingId);

    /** 读取一个入账流。 */
    @Select("SELECT * FROM t_account_posting_stream WHERE entity_type = #{entityType} AND entity_id = #{entityId} "
        + "AND currency = #{currency} AND shard_no = 0")
    Map<String, Object> selectStream(@Param("entityType") String entityType, @Param("entityId") String entityId,
        @Param("currency") String currency);

    /** 读取全部桶移动，按创建顺序。 */
    @Select("SELECT * FROM t_balance_movement ORDER BY create_time, id")
    List<Map<String, Object>> selectMovements();

    /** 统计已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event WHERE event_id = #{eventId}")
    int countConsumedEvent(@Param("eventId") String eventId);

    /** 统计全部已消费的领域事件。 */
    @Select("SELECT COUNT(*) FROM t_consumed_event")
    int countAllConsumedEvents();

    /** 统计尚未完成的补偿任务（发布成功后删除）。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event")
    int countCompensationEvents();

    /** 读取全部补偿任务的状态，用于发布失败时的诊断。 */
    @Select("SELECT event_key, execute_status, last_error_code FROM t_compensation_event ORDER BY id")
    List<Map<String, Object>> selectCompensationEvents();

    /** 插入一个 Owner 账户前置数据；余额以净额给出，借贷合计按正常方向写入。 */
    @Insert("INSERT INTO t_owner_account (id, account_no, entity_type, entity_id, currency, account_code, owner_type, "
        + "owner_id, balance_bucket, normal_side, debit_total, credit_total, net_balance, balance_version, account_status, "
        + "create_time, update_time) VALUES (#{id}, #{accountNo}, #{entityType}, #{entityId}, #{currency}, #{accountCode}, "
        + "#{ownerType}, #{ownerId}, #{bucket}, #{normalSide}, #{debitTotal}, #{creditTotal}, #{netBalance}, 0, #{status}, "
        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))")
    void insertAccount(@Param("id") long id, @Param("accountNo") String accountNo, @Param("entityType") String entityType,
        @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("accountCode") String accountCode, @Param("ownerType") String ownerType,
        @Param("ownerId") String ownerId, @Param("bucket") String bucket, @Param("normalSide") String normalSide,
        @Param("debitTotal") BigDecimal debitTotal, @Param("creditTotal") BigDecimal creditTotal,
        @Param("netBalance") BigDecimal netBalance, @Param("status") String status);

    /** 修改账户状态（本期没有改状态接口，测试以前置数据构造 FROZEN / CLOSED）。 */
    @Update("UPDATE t_owner_account SET account_status = #{status} WHERE id = #{accountId}")
    void updateAccountStatus(@Param("accountId") long accountId, @Param("status") String status);

    /** 直接篡改一个账户的累计值，构造"余额与流水不一致"的重建场景。 */
    @Update("UPDATE t_owner_account SET debit_total = #{debitTotal}, credit_total = #{creditTotal}, net_balance = #{netBalance} "
        + "WHERE id = #{accountId}")
    void tamperBalance(@Param("accountId") long accountId, @Param("debitTotal") BigDecimal debitTotal,
        @Param("creditTotal") BigDecimal creditTotal, @Param("netBalance") BigDecimal netBalance);
}
