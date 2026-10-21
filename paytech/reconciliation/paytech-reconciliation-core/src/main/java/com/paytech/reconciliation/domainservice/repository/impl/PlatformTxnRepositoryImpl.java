package com.paytech.reconciliation.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.mapper.PlatformTxnMapper;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domain.PlatformTxnProgressChange;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;

import lombok.AllArgsConstructor;

/** PlatformTxnRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class PlatformTxnRepositoryImpl implements PlatformTxnRepository {
    private final Clock clock;
    private final PlatformTxnMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(PlatformTxn record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("平台流水已登记: billingFactId=" + record.getBillingFactId(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public PlatformTxn findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；每 1,000 个主键一次查询，合并后按主键升序，空集合不查询。 */
    @Override
    public List<PlatformTxn> findByIds(List<Long> ids) {
        return RepositoryBatches.query(ids, mapper::selectByIds).stream().sorted(Comparator.comparing(PlatformTxn::getId))
            .toList();
    }

    /** 按来源 billing 事实读取（消费幂等）。 */
    @Override
    public PlatformTxn findByBillingFactId(Long billingFactId) {
        return mapper.selectByBillingFactId(billingFactId);
    }

    /** 按清算账户 + 外部交易号读取应收与应付两个方向（账单行匹配）。 */
    @Override
    public List<PlatformTxn> listByReference(Long clearingAccountId, String referenceType,
        String externalTransactionId) {
        return mapper.selectByReference(clearingAccountId, referenceType, externalTransactionId);
    }

    /** 按清算账户 + 一组外部交易号读取；每 1,000 个交易号一次查询，合并后按主键升序。 */
    @Override
    public List<PlatformTxn> listByReferences(Long clearingAccountId, Collection<ExternalReference> references) {
        return RepositoryBatches.query(references, part -> mapper.selectByReferences(clearingAccountId, part)).stream()
            .sorted(Comparator.comparing(PlatformTxn::getId)).toList();
    }

    /** 按可选条件分页读取（清算账户、渠道账单、状态、方向、外部交易号、业务时间区间 [from, to)）；null 条件不过滤。 */
    @Override
    public List<PlatformTxn> findPage(Long clearingAccountId, Long statementId, PlatformTxnStatus status, ClearingDirection direction,
        String externalTransactionId, Instant businessTimeFrom, Instant businessTimeTo, long offset, int limit) {
        return mapper.selectPage(clearingAccountId, statementId, status, direction, externalTransactionId, businessTimeFrom,
            businessTimeTo, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long clearingAccountId, Long statementId, PlatformTxnStatus status, ClearingDirection direction,
        String externalTransactionId, Instant businessTimeFrom, Instant businessTimeTo) {
        return mapper.countPage(clearingAccountId, statementId, status, direction, externalTransactionId, businessTimeFrom,
            businessTimeTo);
    }

    /** 按版本更新对账进度（已匹配、实际费用、已结清金额与状态）；版本不符抛并发冲突。 */
    @Override
    public void updateProgress(PlatformTxn record, BigDecimal matchedAmount, BigDecimal actualFee,
        BigDecimal settledAmount, PlatformTxnStatus status) {
        Instant now = now();
        if (mapper.updateProgress(record, matchedAmount, actualFee, settledAmount, status, now) != 1) {
            throw new ConcurrentRequestException("平台流水已变更，请刷新后重试: " + record.getId());
        }
        record.setMatchedAmount(matchedAmount);
        record.setActualFee(actualFee);
        record.setSettledAmount(settledAmount);
        record.setStatus(status);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 按版本批量更新对账进度：每 1,000 笔一条多行条件更新，命中行数不符即并发冲突；全部成功后同步传入对象。 */
    @Override
    public void updateProgresses(List<PlatformTxnProgressChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Instant now = now();
        List<PlatformTxnProgressChange> ordered = changes.stream()
            .sorted(Comparator.comparing(change -> change.record().getId())).toList();
        for (List<PlatformTxnProgressChange> part : RepositoryBatches.partition(ordered)) {
            if (mapper.updateProgressBatch(part, now) != part.size()) {
                throw new ConcurrentRequestException("平台流水已变更，请刷新后重试: " + part.getFirst().record().getId()
                    + " 起 " + part.size() + " 笔");
            }
        }
        for (PlatformTxnProgressChange change : changes) {
            PlatformTxn record = change.record();
            record.setMatchedAmount(change.matchedAmount());
            record.setActualFee(change.actualFee());
            record.setSettledAmount(change.settledAmount());
            record.setStatus(change.status());
            record.setVersion(record.getVersion() + 1);
            record.setUpdateTime(now);
        }
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
