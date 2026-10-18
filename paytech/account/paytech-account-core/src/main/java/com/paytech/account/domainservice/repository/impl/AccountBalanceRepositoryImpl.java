package com.paytech.account.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.core.dal.mapper.AccountBalanceMapper;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domainservice.repository.AccountBalanceRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountBalanceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountBalanceRepositoryImpl implements AccountBalanceRepository {
    private final Clock clock;
    private final AccountBalanceMapper mapper;

    /** 唯一键冲突说明另一个分区刚创建了同一余额，转为可重试的并发冲突。 */
    @Override
    public void insert(AccountBalance balance) {
        try {
            mapper.insert(balance);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("余额账户被并发创建: " + balance.getAccountCode() + "/"
                + balance.getOwnerType().getCode() + "/" + balance.getOwnerId(), e);
        }
    }

    /** 按余额身份读取，不存在返回空。 */
    @Override
    public AccountBalance findByIdentity(BookIdentity book, String accountCode, OwnerType ownerType, String ownerId,
        BalanceBucket bucket) {
        return mapper.selectByIdentity(book.entityType(), book.entityId(), book.currency(), accountCode, ownerType, ownerId, bucket);
    }

    /** 按主键升序读取一组余额；空集合返回空列表。 */
    @Override
    public List<AccountBalance> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 读取账簿主体的全部余额。 */
    @Override
    public List<AccountBalance> findByBook(BookIdentity book) {
        return mapper.selectByBook(book.entityType(), book.entityId(), book.currency());
    }

    /** 以排他锁读取账簿主体的全部余额。 */
    @Override
    public List<AccountBalance> lockByBook(BookIdentity book) {
        return mapper.selectByBookForUpdate(book.entityType(), book.entityId(), book.currency());
    }

    /** 读取一个 Owner 在账簿主体内的全部余额。 */
    @Override
    public List<AccountBalance> findByOwner(BookIdentity book, OwnerType ownerType, String ownerId) {
        return mapper.selectByOwner(book.entityType(), book.entityId(), book.currency(), ownerType, ownerId);
    }

    /** 存在余额的全部账簿主体。 */
    @Override
    public List<BookIdentity> findBookIdentities() {
        return mapper.selectBookIdentities().stream().map(row -> new BookIdentity(
            BookEntityType.valueOf((String)row.get("entityType")), (String)row.get("entityId"),
            (String)row.get("currency"))).toList();
    }

    /** 按 Owner 类型统计账簿主体内净余额非零的余额数。 */
    @Override
    public Map<OwnerType, Long> countNonZeroByOwnerType(BookIdentity book) {
        Map<OwnerType, Long> counts = new EnumMap<>(OwnerType.class);
        for (Map<String, Object> row : mapper.countNonZeroByOwnerType(book.entityType(), book.entityId(), book.currency())) {
            counts.put(OwnerType.valueOf((String)row.get("ownerType")), ((Number)row.get("nonZero")).longValue());
        }
        return counts;
    }

    /** 以余额版本 CAS 写入，成功后同步原对象。 */
    @Override
    public void applyChange(AccountBalance balance, BigDecimal debitTotal, BigDecimal creditTotal,
        BigDecimal netBalance, long factId, long factVersion) {
        Instant now = now();
        if (mapper.applyChange(balance, debitTotal, creditTotal, netBalance, factId, factVersion, now) != 1) {
            throw new ConcurrentRequestException("余额已被并发更新: " + balance.getId());
        }
        balance.setDebitTotal(debitTotal);
        balance.setCreditTotal(creditTotal);
        balance.setNetBalance(netBalance);
        balance.setBalanceVersion(balance.getBalanceVersion() + 1);
        balance.setLastFactId(factId);
        balance.setLastFactVersion(factVersion);
        balance.setUpdateTime(now);
    }

    /** 把账簿主体的全部余额归零。 */
    @Override
    public void resetBook(BookIdentity book) {
        mapper.resetBook(book.entityType(), book.entityId(), book.currency(), now());
    }

    /** 当前 UTC 时间，截断到微秒。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
