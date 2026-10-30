package com.paytech.account.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.OwnerAccount;

/** t_owner_account：Owner 账户与实时余额持久化。 */
@Mapper
public interface OwnerAccountMapper {

    /** 插入一行。 */
    int insert(OwnerAccount record);

    /** 按账户身份唯一键读取，不存在返回空。 */
    OwnerAccount selectByIdentity(@Param("entityType") AccountEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("accountCode") String accountCode,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId, @Param("bucket") BalanceBucket bucket);

    /** 按主键读取，不存在返回空。 */
    OwnerAccount selectById(@Param("id") long id);

    /** 按主键升序读取一组账户。 */
    List<OwnerAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按主键升序以排他锁读取一组账户；必须在事务内调用。 */
    List<OwnerAccount> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    /** 以排他锁读取主体与币种下的全部账户，按主键升序；必须在事务内调用。 */
    List<OwnerAccount> selectByEntityForUpdate(@Param("entityType") AccountEntityType entityType,
        @Param("entityId") String entityId, @Param("currency") String currency);

    /** 读取一个核算对象在主体与币种下的全部账户，按主键升序。 */
    List<OwnerAccount> selectByOwner(@Param("entityType") AccountEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId);

    /** 读取一个核算对象在全部经营主体与币种下的账户，按主体、币种、主键排序。 */
    List<OwnerAccount> selectByOwnerAcrossEntities(@Param("entityType") AccountEntityType entityType,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId);

    /** 按筛选条件分页读取，按科目代码、核算对象类型、核算对象标识、余额桶、主键排序；账户号为精确匹配。 */
    List<OwnerAccount> selectPage(@Param("entityType") AccountEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId,
        @Param("accountCode") String accountCode, @Param("bucket") BalanceBucket bucket,
        @Param("accountNo") String accountNo, @Param("offset") int offset, @Param("limit") int limit);

    /** 与 selectPage 相同筛选条件的总数。 */
    long countPage(@Param("entityType") AccountEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId,
        @Param("accountCode") String accountCode, @Param("bucket") BalanceBucket bucket,
        @Param("accountNo") String accountNo);

    /**
     * 以余额版本 CAS 写入新的累计值并加一
     *
     * @return 受影响行数
     */
    int applyChange(@Param("account") OwnerAccount account, @Param("debitTotal") BigDecimal debitTotal,
        @Param("creditTotal") BigDecimal creditTotal, @Param("netBalance") BigDecimal netBalance, @Param("now") Instant now);
}
