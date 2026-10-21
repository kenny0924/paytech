package com.paytech.reconciliation.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_clearing_account：渠道清算账户的 MyBatis 映射；SQL 位于 mapper/reconciliation/ClearingAccountMapper.xml。 */
@Mapper
public interface ClearingAccountMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ClearingAccount record);

    /** 按主键读取，不存在返回 null。 */
    ClearingAccount selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<ClearingAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按清算账户编码读取，不存在返回 null。 */
    ClearingAccount selectByCode(@Param("clearingAccountCode") String clearingAccountCode);

    /** 按可选条件分页读取，排序稳定。 */
    List<ClearingAccount> selectPage(@Param("entityNo") String entityNo, @Param("channelCode") String channelCode,
        @Param("currency") String currency, @Param("status") ClearingAccountStatus status,
        @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("entityNo") String entityNo, @Param("channelCode") String channelCode,
        @Param("currency") String currency, @Param("status") ClearingAccountStatus status);

    /** 按版本更新三项配置与到账银行账户。 */
    int updateSettings(@Param("record") ClearingAccount record, @Param("unmatchedWaitDays") Integer unmatchedWaitDays,
        @Param("costToleranceAmount") BigDecimal costToleranceAmount, @Param("cashGraceDays") Integer cashGraceDays,
        @Param("settlementCashAccountId") Long settlementCashAccountId, @Param("now") Instant now);

    /** 按版本与原状态更新状态。 */
    int changeStatus(@Param("record") ClearingAccount record, @Param("status") ClearingAccountStatus status,
        @Param("now") Instant now);

    /** 只推进版本（挂 / 摘 MID 时与 MID 行在同一事务串行化）。 */
    int increaseVersion(@Param("record") ClearingAccount record, @Param("now") Instant now);
}
