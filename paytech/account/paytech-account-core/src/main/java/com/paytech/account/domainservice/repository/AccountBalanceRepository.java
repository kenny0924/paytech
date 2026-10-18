package com.paytech.account.domainservice.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.BookIdentity;

/** t_account_balance：Owner 余额桶读写。 */
public interface AccountBalanceRepository {

    /** 插入余额；同一余额身份已被并发创建时以并发冲突失败，由消费重投重试。 */
    void insert(AccountBalance balance);

    /** 按余额身份读取，不存在返回空。 */
    AccountBalance findByIdentity(BookIdentity book, String accountCode, OwnerType ownerType, String ownerId,
        BalanceBucket bucket);

    /** 按主键升序读取一组余额；空集合返回空列表。 */
    List<AccountBalance> findByIds(List<Long> ids);

    /** 读取账簿主体的全部余额，按科目代码、核算对象、余额桶排序。 */
    List<AccountBalance> findByBook(BookIdentity book);

    /** 以排他锁读取账簿主体的全部余额；必须在事务内调用。 */
    List<AccountBalance> lockByBook(BookIdentity book);

    /** 读取一个 Owner 在账簿主体内的全部余额。 */
    List<AccountBalance> findByOwner(BookIdentity book, OwnerType ownerType, String ownerId);

    /** 存在余额的全部账簿主体。 */
    List<BookIdentity> findBookIdentities();

    /** 按 Owner 类型统计账簿主体内净余额非零的余额数；没有非零余额的类型不出现。 */
    Map<OwnerType, Long> countNonZeroByOwnerType(BookIdentity book);

    /** 以余额版本 CAS 写入新的累计值与最近事实，成功后同步原对象；版本不符时以并发冲突失败。 */
    void applyChange(AccountBalance balance, BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal netBalance,
        long factId, long factVersion);

    /** 把账簿主体的全部余额归零（重建使用）；必须在事务内调用。 */
    void resetBook(BookIdentity book);
}
