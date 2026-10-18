package com.paytech.account.core.dal.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.core.dal.dataobject.BalanceAppliedEntry;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.BookEntityType;

/** t_balance_applied_fact 与 t_balance_applied_entry：已处理经济事实及其余额变化。 */
@Mapper
public interface BalanceAppliedFactMapper {

    /** 插入一条已处理事实。 */
    int insert(BalanceAppliedFact record);

    /** 以排他锁读取一个经济事实的处理记录，不存在返回空；必须在事务内调用。 */
    BalanceAppliedFact selectByFactIdForUpdate(@Param("factId") long factId);

    /**
     * 以原状态为条件迁移状态并写入处理信息
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("fact") BalanceAppliedFact fact, @Param("status") AppliedFactStatus status,
        @Param("now") Instant now);

    /** 账簿主体内已处理（应用、冲回、跳过）事实的数量与最大 factId；每行含 factCount 与 maxFactId。 */
    Map<String, Object> selectProgress(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency);

    /** 统计账簿主体内 factId 大于给定值的处理记录（含预登记）。 */
    long countAfter(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("factId") long factId);

    /** 删除经营主体账簿内 factId 不大于给定值的事实的余额变化。 */
    int deleteEntriesUpTo(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("factId") long factId);

    /** 删除经营主体账簿内 factId 不大于给定值的处理记录。 */
    int deleteFactsUpTo(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("factId") long factId);

    /** 批量插入余额变化。 */
    int insertEntries(@Param("entries") List<BalanceAppliedEntry> entries);

    /** 读取一条已处理事实的全部余额变化，按主键升序。 */
    List<BalanceAppliedEntry> selectEntries(@Param("appliedFactId") long appliedFactId);
}
