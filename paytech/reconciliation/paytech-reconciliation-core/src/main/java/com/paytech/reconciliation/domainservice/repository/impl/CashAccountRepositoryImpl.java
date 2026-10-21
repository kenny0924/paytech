package com.paytech.reconciliation.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.mapper.CashAccountMapper;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;

import lombok.AllArgsConstructor;

/** CashAccountRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class CashAccountRepositoryImpl implements CashAccountRepository {
    private final Clock clock;
    private final CashAccountMapper mapper;

    /** 插入新银行账户（PENDING_VERIFICATION）；同主体 + 币种 + 用途的 ACTIVE 唯一性由复核通过时检查，库内无唯一约束。 */
    @Override
    public void insert(CashAccount record) {
        mapper.insert(record);
    }

    /** 按主键读取。 */
    @Override
    public CashAccount findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；空集合不查询。 */
    @Override
    public List<CashAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按主体 + 币种 + 用途 + 状态读取（当前账户查询、ACTIVE 唯一性检查）。 */
    @Override
    public List<CashAccount> listByScope(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose accountPurpose, CashAccountStatus status) {
        return mapper.selectByScope(entityType, entityId, currency, accountPurpose, status);
    }

    /** 按可选条件分页读取（主体类型、主体标识、币种、用途、状态）；null 条件不过滤。 */
    @Override
    public List<CashAccount> findPage(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose accountPurpose, CashAccountStatus status, long offset, int limit) {
        return mapper.selectPage(entityType, entityId, currency, accountPurpose, status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose accountPurpose, CashAccountStatus status) {
        return mapper.countPage(entityType, entityId, currency, accountPurpose, status);
    }

    /** 按版本与原状态更新状态与复核列（申请关闭、复核通过 / 拒绝）；不变的列传原值。 */
    @Override
    public void updateReview(CashAccount record, CashAccountStatus status, CashAccountPendingAction pendingAction,
        String contentHash, Long makerId, Long checkerId) {
        Instant now = now();
        if (mapper.updateReview(record, status, pendingAction, contentHash, makerId, checkerId, now) != 1) {
            throw new ConcurrentRequestException("银行账户已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setPendingAction(pendingAction);
        record.setContentHash(contentHash);
        record.setMakerId(makerId);
        record.setCheckerId(checkerId);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
