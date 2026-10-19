package com.paytech.account.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.OwnerAccount;
import com.paytech.account.core.dal.mapper.OwnerAccountMapper;
import com.paytech.account.domainservice.repository.OwnerAccountRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;

/** OwnerAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class OwnerAccountRepositoryImpl implements OwnerAccountRepository {
    private final OwnerAccountMapper mapper;

    /** 唯一键冲突说明同一账户刚被并发开立，转为可重试的并发冲突。 */
    @Override
    public void insert(OwnerAccount account) {
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("Owner 账户被并发开立: " + account.getAccountCode() + "/"
                + account.getOwnerType().getCode() + "/" + account.getOwnerId(), e);
        }
    }

    /** 按账户身份读取。 */
    @Override
    public OwnerAccount findByIdentity(AccountEntityType entityType, String entityId, String currency,
        String accountCode, OwnerType ownerType, String ownerId, BalanceBucket bucket) {
        return mapper.selectByIdentity(entityType, entityId, currency, accountCode, ownerType, ownerId, bucket);
    }

    /** 按主键读取。 */
    @Override
    public OwnerAccount findById(long id) {
        return mapper.selectById(id);
    }

    /** 空集合不查询。 */
    @Override
    public List<OwnerAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 空集合不查询。 */
    @Override
    public List<OwnerAccount> lockByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIdsForUpdate(ids);
    }

    /** 以排他锁读取主体与币种下的全部账户。 */
    @Override
    public List<OwnerAccount> lockByEntity(AccountEntityType entityType, String entityId, String currency) {
        return mapper.selectByEntityForUpdate(entityType, entityId, currency);
    }

    /** 读取一个核算对象的全部账户。 */
    @Override
    public List<OwnerAccount> findByOwner(AccountEntityType entityType, String entityId, String currency,
        OwnerType ownerType, String ownerId) {
        return mapper.selectByOwner(entityType, entityId, currency, ownerType, ownerId);
    }

    /** 数据库 LIMIT / OFFSET 分页。 */
    @Override
    public List<OwnerAccount> findPage(AccountEntityType entityType, String entityId, String currency,
        OwnerType ownerType, String ownerId, String accountCode, BalanceBucket bucket, PagingRequestBody paging) {
        return mapper.selectPage(entityType, entityId, currency, ownerType, ownerId, accountCode, bucket,
            paging.offset(), paging.size());
    }

    /** 同筛选条件的总数。 */
    @Override
    public long count(AccountEntityType entityType, String entityId, String currency, OwnerType ownerType,
        String ownerId, String accountCode, BalanceBucket bucket) {
        return mapper.countPage(entityType, entityId, currency, ownerType, ownerId, accountCode, bucket);
    }

    /** 以余额版本 CAS 写入，成功后同步原对象。 */
    @Override
    public void applyChange(OwnerAccount account, BigDecimal debitTotal, BigDecimal creditTotal,
        BigDecimal netBalance, Instant now) {
        if (mapper.applyChange(account, debitTotal, creditTotal, netBalance, now) != 1) {
            throw new ConcurrentRequestException("Owner 账户余额已被并发更新: " + account.getId());
        }
        account.setDebitTotal(debitTotal);
        account.setCreditTotal(creditTotal);
        account.setNetBalance(netBalance);
        account.setBalanceVersion(account.getBalanceVersion() + 1);
        account.setUpdateTime(now);
    }
}
