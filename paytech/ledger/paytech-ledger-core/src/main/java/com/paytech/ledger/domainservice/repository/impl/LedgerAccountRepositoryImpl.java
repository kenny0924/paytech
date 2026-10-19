package com.paytech.ledger.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.mapper.LedgerAccountMapper;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;

import lombok.AllArgsConstructor;

/** LedgerAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class LedgerAccountRepositoryImpl implements LedgerAccountRepository {
    private final LedgerAccountMapper mapper;

    /** 插入账户；同一账户身份已存在时拒绝。 */
    @Override
    public void insert(LedgerAccount account) {
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new FailException(LedgerSetupErrorCode.ACCOUNT_ALREADY_EXISTS, null, e);
        }
    }

    /** 唯一键冲突说明另一个分区刚创建了同一账户身份，转为可重试的并发冲突。 */
    @Override
    public void insertConcurrently(LedgerAccount account) {
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("账户被并发创建: 账簿 " + account.getLedgerBookId() + ", Owner "
                + account.getOwnerType().getCode() + "/" + account.getOwnerId(), e);
        }
    }

    /** 按主键读取账户，不存在返回空。 */
    @Override
    public LedgerAccount findById(long id) {
        return mapper.selectById(id);
    }

    /** 按主键升序读取一组账户，不加锁。 */
    @Override
    public List<LedgerAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按过滤条件分页读取账簿内账户、科目代码与分录聚合余额。 */
    @Override
    public List<AccountView> findPage(AccountQuery query) {
        return mapper.selectPage(query, query.getPaging().size(), query.getPaging().offset());
    }

    /** 统计符合过滤条件的账户数量。 */
    @Override
    public long countPage(AccountQuery query) {
        return mapper.countPage(query);
    }

    /** 按账户身份唯一键读取，不存在返回空。 */
    @Override
    public LedgerAccount findByIdentity(long ledgerBookId, long chartAccountId, OwnerType ownerType, String ownerId,
        BalanceBucket bucket) {
        return mapper.selectByIdentity(ledgerBookId, chartAccountId, ownerType, ownerId, bucket);
    }

    /** 读取账簿内指定核算对象类型的全部账户与分录聚合余额。 */
    @Override
    public List<AccountView> findOwnerTotals(long ledgerBookId, Collection<OwnerType> ownerTypes) {
        return mapper.selectOwnerTotals(ledgerBookId, ownerTypes);
    }

    /** 统计一组账簿内指定核算对象类型、分录聚合净额非零的账户数；无账簿时为零。 */
    @Override
    public long countNonZero(List<Long> bookIds, Collection<OwnerType> ownerTypes) {
        return bookIds.isEmpty() ? 0 : mapper.countNonZeroByOwnerTypes(bookIds, ownerTypes);
    }

    /** 从账户全部分录行聚合借贷累计与最后分录序号。 */
    @Override
    public AccountJournalTotalsView findJournalTotals(long accountId) {
        return mapper.selectJournalTotals(accountId);
    }
}
