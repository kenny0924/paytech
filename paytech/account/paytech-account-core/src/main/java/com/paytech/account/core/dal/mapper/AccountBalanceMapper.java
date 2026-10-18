package com.paytech.account.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.core.enums.BookEntityType;

/** t_account_balance：Owner 余额桶持久化。 */
@Mapper
public interface AccountBalanceMapper {

    /** 插入一行。 */
    int insert(AccountBalance record);

    /** 按余额身份唯一键读取，不存在返回空。 */
    AccountBalance selectByIdentity(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("accountCode") String accountCode,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId, @Param("bucket") BalanceBucket bucket);

    /** 按主键升序读取一组余额。 */
    List<AccountBalance> selectByIds(@Param("ids") List<Long> ids);

    /** 读取账簿主体的全部余额，按科目代码、核算对象、余额桶排序。 */
    List<AccountBalance> selectByBook(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency);

    /** 以排他锁读取账簿主体的全部余额；必须在事务内调用。 */
    List<AccountBalance> selectByBookForUpdate(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency);

    /** 读取一个 Owner 在账簿主体内的全部余额。 */
    List<AccountBalance> selectByOwner(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("ownerType") OwnerType ownerType,
        @Param("ownerId") String ownerId);

    /** 读取存在余额的全部账簿主体；每行含 entityType、entityId 与 currency。 */
    List<Map<String, Object>> selectBookIdentities();

    /** 按 Owner 类型统计账簿主体内净余额非零的余额数；每行含 ownerType 与 nonZero。 */
    List<Map<String, Object>> countNonZeroByOwnerType(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency);

    /**
     * 以余额版本 CAS 写入新的累计值与最近事实
     *
     * @return 受影响行数
     */
    int applyChange(@Param("balance") AccountBalance balance, @Param("debitTotal") BigDecimal debitTotal,
        @Param("creditTotal") BigDecimal creditTotal, @Param("netBalance") BigDecimal netBalance,
        @Param("factId") long factId, @Param("factVersion") long factVersion, @Param("now") Instant now);

    /** 把账簿主体的全部余额归零并推进版本（重建使用）。 */
    int resetBook(@Param("bookEntityType") BookEntityType bookEntityType,
        @Param("bookEntityId") String bookEntityId, @Param("currency") String currency, @Param("now") Instant now);
}
