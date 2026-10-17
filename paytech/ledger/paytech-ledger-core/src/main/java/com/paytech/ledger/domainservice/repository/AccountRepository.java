package com.paytech.ledger.domainservice.repository;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.dal.dataobject.Account;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domain.view.AccountProjectionCheckView;
import com.paytech.ledger.domain.view.AccountView;

/** t_Account：统一记账账户读写。 */
public interface AccountRepository {

    /** 插入账户；同一账户身份已存在时拒绝。 */
    void insert(Account account);

    /** 按主键读取账户，不存在返回空。 */
    Account findById(long id);

    /** 按主键升序读取一组账户，不加锁。 */
    List<Account> findByIds(List<Long> ids);

    /** 按主键升序以排他锁读取一组账户；必须在事务内调用。 */
    List<Account> lockByIds(List<Long> ids);

    /** 以余额版本 CAS 写入新的余额投影，成功后同步原对象；版本或序号不符时失败。 */
    void applyPosting(Account account, BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal netBalance, long journalSequence);

    /** 按过滤条件分页读取账簿内账户及科目代码。 */
    List<AccountView> findPage(AccountQuery query);

    /** 统计符合过滤条件的账户数量。 */
    long countPage(AccountQuery query);

    /** 读取账户投影与水位内分录合计的对照。 */
    List<AccountProjectionCheckView> findProjectionChecks(long ledgerBookId, long asOfSequence);

    /** 按账户身份唯一键读取，不存在返回空。 */
    Account findByIdentity(long ledgerBookId, long chartAccountId, OwnerType ownerType, String ownerId, BalanceBucket bucket);

    /** 以排他锁读取一个账户；必须在事务内调用。 */
    Account lockById(long id);

    /** 从账户全部分录行重算借贷累计与最后分录序号。 */
    AccountJournalTotalsView findJournalTotals(long accountId);

    /** 以余额版本 CAS 写入重建后的投影，成功后同步原对象；版本不符时失败。 */
    void rebuildProjection(Account account, BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal netBalance,
        long lastJournalSequence);
}
