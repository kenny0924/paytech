package com.paytech.reconciliation.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.mapper.ExpectedBankMovementMapper;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;

import lombok.AllArgsConstructor;

/** ExpectedBankMovementRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ExpectedBankMovementRepositoryImpl implements ExpectedBankMovementRepository {
    private final Clock clock;
    private final ExpectedBankMovementMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ExpectedBankMovement record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("预期银行流水已生成: " + record.getMovementType().getCode() + "/" + record.getSourceId(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ExpectedBankMovement findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；空集合不查询。 */
    @Override
    public List<ExpectedBankMovement> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按预期来源读取（渠道账单对平生成的预期流水）。 */
    @Override
    public ExpectedBankMovement findBySource(ExpectedMovementType movementType, Long sourceId) {
        return mapper.selectBySource(movementType, sourceId);
    }

    /** 读取银行账户下 OPEN / PARTIALLY_CONFIRMED 的预期流水（匹配建议）。 */
    @Override
    public List<ExpectedBankMovement> listOpen(Long cashAccountId) {
        return mapper.selectOpen(cashAccountId);
    }

    /** 按可选条件分页读取（银行账户、来源类型、来源 ID、状态）；null 条件不过滤。 */
    @Override
    public List<ExpectedBankMovement> findPage(Long cashAccountId, ExpectedMovementType movementType, Long sourceId,
        ExpectedMovementStatus status, long offset, int limit) {
        return mapper.selectPage(cashAccountId, movementType, sourceId, status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long cashAccountId, ExpectedMovementType movementType, Long sourceId,
        ExpectedMovementStatus status) {
        return mapper.countPage(cashAccountId, movementType, sourceId, status);
    }

    /** 按版本更新已确认金额、归类金额与状态。 */
    @Override
    public void updateProgress(ExpectedBankMovement record, BigDecimal confirmedAmount, BigDecimal classifiedAmount,
        ExpectedMovementStatus status) {
        Instant now = now();
        if (mapper.updateProgress(record, confirmedAmount, classifiedAmount, status, now) != 1) {
            throw new ConcurrentRequestException("预期银行流水已变更，请刷新后重试: " + record.getId());
        }
        record.setConfirmedAmount(confirmedAmount);
        record.setClassifiedAmount(classifiedAmount);
        record.setStatus(status);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
