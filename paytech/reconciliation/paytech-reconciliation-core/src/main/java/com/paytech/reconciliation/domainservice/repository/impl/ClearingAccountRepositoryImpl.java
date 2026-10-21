package com.paytech.reconciliation.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.mapper.ClearingAccountMapper;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;

import lombok.AllArgsConstructor;

/** ClearingAccountRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ClearingAccountRepositoryImpl implements ClearingAccountRepository {
    private final Clock clock;
    private final ClearingAccountMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ClearingAccount record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("清算账户编码已存在: " + record.getClearingAccountCode(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ClearingAccount findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；空集合不查询。 */
    @Override
    public List<ClearingAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按清算账户编码读取（渠道账单文件用编码指明清算账户）。 */
    @Override
    public ClearingAccount findByCode(String clearingAccountCode) {
        return mapper.selectByCode(clearingAccountCode);
    }

    /** 按可选条件分页读取（经营主体、渠道、币种、状态）；null 条件不过滤。 */
    @Override
    public List<ClearingAccount> findPage(String entityNo, String channelCode, String currency,
        ClearingAccountStatus status, long offset, int limit) {
        return mapper.selectPage(entityNo, channelCode, currency, status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(String entityNo, String channelCode, String currency, ClearingAccountStatus status) {
        return mapper.countPage(entityNo, channelCode, currency, status);
    }

    /** 按版本更新三项配置与到账银行账户；版本不符抛并发冲突，不修改传入对象。 */
    @Override
    public void updateSettings(ClearingAccount record, Integer unmatchedWaitDays, BigDecimal costToleranceAmount,
        Integer cashGraceDays, Long settlementCashAccountId) {
        Instant now = now();
        if (mapper.updateSettings(record, unmatchedWaitDays, costToleranceAmount, cashGraceDays,
            settlementCashAccountId, now) != 1) {
            throw new ConcurrentRequestException("清算账户已变更，请刷新后重试: " + record.getId());
        }
        record.setUnmatchedWaitDays(unmatchedWaitDays);
        record.setCostToleranceAmount(costToleranceAmount);
        record.setCashGraceDays(cashGraceDays);
        record.setSettlementCashAccountId(settlementCashAccountId);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 按版本与原状态启用或停用；冲突抛并发冲突。 */
    @Override
    public void changeStatus(ClearingAccount record, ClearingAccountStatus status) {
        Instant now = now();
        if (mapper.changeStatus(record, status, now) != 1) {
            throw new ConcurrentRequestException("清算账户已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 只推进版本：挂入或摘除 MID 时与 t_clearing_account_mid 写入同一事务，按版本串行化同一清算账户的 MID 变更。 */
    @Override
    public void increaseVersion(ClearingAccount record) {
        Instant now = now();
        if (mapper.increaseVersion(record, now) != 1) {
            throw new ConcurrentRequestException("清算账户已变更，请刷新后重试: " + record.getId());
        }
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
