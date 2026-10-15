package com.paytech.finance.accounting.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.mapper.AccountMapper;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.domain.AccountQuery;
import com.paytech.finance.accounting.domain.view.AccountView;
import com.paytech.finance.accounting.domain.view.OwnerAccountBalanceView;
import com.paytech.finance.accounting.domainservice.repository.AccountRepository;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {
    private final Clock clock;
    private final AccountMapper mapper;

    /** 插入账户；同一账户身份已存在时拒绝。 */
    @Override
    public void insert(Account account) {
        insertUnique(() -> mapper.insert(account), "账户已存在: 账簿 " + account.getLedgerBookId() + ", Owner " + account.getOwnerType().getCode() + "/" + account.getOwnerId() + ", 余额桶 " + account.getBalanceBucket().getCode());
    }

    /** 按主键读取账户，不存在返回空。 */
    @Override
    public Account findById(long id) {
        return mapper.selectById(id);
    }

    /** 按主键升序读取一组账户，不加锁。 */
    @Override
    public List<Account> findByIds(List<Long> ids) {
        return mapper.selectByIds(ids);
    }

    /** 按主键升序以排他锁读取一组账户；必须在事务内调用。 */
    @Override
    public List<Account> lockByIds(List<Long> ids) {
        return mapper.selectByIdsForUpdate(ids);
    }

    /** 以余额版本 CAS 写入新的余额投影，成功后同步原对象；版本或序号不符时失败。 */
    @Override
    public void applyPosting(Account account, BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal netBalance, long journalSequence) {
        Instant now = now();
        if (mapper.applyPosting(account, debitTotal, creditTotal, netBalance, journalSequence, now) != 1) {
            throw new ConcurrentRequestException("账户余额已被并发更新: " + account.getId());
        }
        account.setDebitTotal(debitTotal);
        account.setCreditTotal(creditTotal);
        account.setNetBalance(netBalance);
        account.setLastJournalSequence(journalSequence);
        account.setBalanceVersion(account.getBalanceVersion() + 1);
        account.setUpdateTime(now);
    }

    /** 读取 Owner 在一个账簿内的全部账户及科目。 */
    @Override
    public List<OwnerAccountBalanceView> findOwnerBalances(long ledgerBookId, OwnerType ownerType, String ownerId) {
        return mapper.selectOwnerBalances(ledgerBookId, ownerType, ownerId);
    }

    /** 按过滤条件分页读取账簿内账户及科目代码。 */
    @Override
    public List<AccountView> findPage(AccountQuery query) {
        return mapper.selectPage(query, query.getPaging().size(), query.getPaging().offset());
    }

    /** 统计符合过滤条件的账户数量。 */
    @Override
    public long countPage(AccountQuery query) {
        return mapper.countPage(query);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 执行插入，业务唯一键冲突时转换为带上下文的业务拒绝。 */
    private void insertUnique(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new BizCheckFailException(message, e);
        }
    }
}
