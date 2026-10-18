package com.paytech.ledger.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domain.view.AccountView;

/** t_ledger_account：会计科目账户身份读写与按 Journal 聚合的余额读取。 */
public interface LedgerAccountRepository {

    /** 插入账户；同一账户身份已存在时拒绝。 */
    void insert(LedgerAccount account);

    /** 消费路径插入账户；同一账户身份已被并发创建时以并发冲突失败，由消息重投重试。 */
    void insertConcurrently(LedgerAccount account);

    /** 按主键读取账户，不存在返回空。 */
    LedgerAccount findById(long id);

    /** 按主键升序读取一组账户，不加锁。 */
    List<LedgerAccount> findByIds(List<Long> ids);

    /** 按过滤条件分页读取账簿内账户、科目代码与分录聚合余额。 */
    List<AccountView> findPage(AccountQuery query);

    /** 统计符合过滤条件的账户数量。 */
    long countPage(AccountQuery query);

    /** 按账户身份唯一键读取，不存在返回空。 */
    LedgerAccount findByIdentity(long ledgerBookId, long chartAccountId, OwnerType ownerType, String ownerId, BalanceBucket bucket);

    /** 读取账簿内指定核算对象类型的全部账户与分录聚合余额。 */
    List<AccountView> findOwnerTotals(long ledgerBookId, Collection<OwnerType> ownerTypes);

    /** 统计一组账簿内指定核算对象类型、分录聚合净额非零的账户数。 */
    long countNonZero(List<Long> bookIds, Collection<OwnerType> ownerTypes);

    /** 从账户全部分录行聚合借贷累计与最后分录序号。 */
    AccountJournalTotalsView findJournalTotals(long accountId);
}
