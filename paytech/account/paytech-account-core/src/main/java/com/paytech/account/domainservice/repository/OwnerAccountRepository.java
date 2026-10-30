package com.paytech.account.domainservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** t_owner_account：Owner 账户与实时余额读写。 */
public interface OwnerAccountRepository {

    /** 插入账户；同一账户身份已被并发创建时以并发冲突失败。 */
    void insert(OwnerAccount account);

    /** 按账户身份读取，不存在返回空。 */
    OwnerAccount findByIdentity(AccountEntityType entityType, String entityId, String currency, String accountCode,
        OwnerType ownerType, String ownerId, BalanceBucket bucket);

    /** 按主键读取，不存在返回空。 */
    OwnerAccount findById(long id);

    /** 按主键升序读取一组账户；空集合返回空列表。 */
    List<OwnerAccount> findByIds(List<Long> ids);

    /** 按主键升序以排他锁读取一组账户；必须在事务内调用。 */
    List<OwnerAccount> lockByIds(List<Long> ids);

    /** 以排他锁读取主体与币种下的全部账户；必须在事务内调用。 */
    List<OwnerAccount> lockByEntity(AccountEntityType entityType, String entityId, String currency);

    /** 读取一个核算对象在主体与币种下的全部账户。 */
    List<OwnerAccount> findByOwner(AccountEntityType entityType, String entityId, String currency, OwnerType ownerType,
        String ownerId);

    /** 读取一个核算对象在指定主体类型下全部主体与币种的账户，按主体、币种、主键排序。 */
    List<OwnerAccount> findByOwnerAcrossEntities(AccountEntityType entityType, OwnerType ownerType, String ownerId);

    /** 按可选筛选条件分页读取主体与币种下的账户；账户号为精确匹配。 */
    List<OwnerAccount> findPage(AccountEntityType entityType, String entityId, String currency, OwnerType ownerType,
        String ownerId, String accountCode, BalanceBucket bucket, String accountNo, PagingRequestBody paging);

    /** 与 findPage 相同筛选条件的总数。 */
    long count(AccountEntityType entityType, String entityId, String currency, OwnerType ownerType, String ownerId,
        String accountCode, BalanceBucket bucket, String accountNo);

    /** 以余额版本 CAS 写入新的累计值，成功后同步原对象；版本不符时以并发冲突失败。 */
    void applyChange(OwnerAccount account, BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal netBalance,
        Instant now);
}
