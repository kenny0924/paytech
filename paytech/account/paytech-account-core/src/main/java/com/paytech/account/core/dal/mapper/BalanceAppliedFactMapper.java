package com.paytech.account.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.enums.AppliedFactStatus;

/** t_balance_applied_fact：已处理计费事实。 */
@Mapper
public interface BalanceAppliedFactMapper {

    /** 插入一条处理记录。 */
    int insert(BalanceAppliedFact record);

    /** 以排他锁读取一个计费事实的处理记录（同 fact_id 取最高版本），不存在返回空；必须在事务内调用。 */
    BalanceAppliedFact selectByFactIdForUpdate(@Param("factId") long factId);

    /** 读取一组计费事实的处理记录。 */
    List<BalanceAppliedFact> selectByFactIds(@Param("factIds") List<Long> factIds);

    /**
     * 以原状态为条件迁移状态并写入处理信息、批次与到期字段
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("fact") BalanceAppliedFact fact, @Param("status") AppliedFactStatus status,
        @Param("now") Instant now);

    /** 经营主体与币种下自身事件已到达的事实数与最大 factId；每行含 factCount 与 maxFactId。 */
    Map<String, Object> selectOperatingEntityProgress(@Param("entityNo") String entityNo,
        @Param("currency") String currency);

    /** Finance Entity 与币种下自身事件已到达的事实数与最大 factId；每行含 factCount 与 maxFactId。 */
    Map<String, Object> selectFinanceEntityProgress(@Param("financeEntityId") long financeEntityId,
        @Param("currency") String currency);

    /** 引用 Finance Entity 的事实所在的全部经营主体编号，升序去重。 */
    List<String> selectEntityNosByFinanceEntity(@Param("financeEntityId") long financeEntityId,
        @Param("currency") String currency);

    /** 到期待移动的事实：maturity_status = PENDING、到期时间不晚于 asOf、主键大于游标，按主键升序。 */
    List<BalanceAppliedFact> selectMaturityDue(@Param("asOf") Instant asOf, @Param("afterId") long afterId,
        @Param("limit") int limit);

    /**
     * 以 PENDING 为条件标记到期完成
     *
     * @return 受影响行数
     */
    int markMatured(@Param("id") long id, @Param("maturedTime") Instant maturedTime);
}
