package com.paytech.reconciliation.domainservice.repository.impl;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.mapper.ChannelStatementMapper;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;

import lombok.AllArgsConstructor;

/** ChannelStatementRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ChannelStatementRepositoryImpl implements ChannelStatementRepository {
    private final Clock clock;
    private final ChannelStatementMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ChannelStatement record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("渠道账单版本已存在: " + record.getChannelBatchRef() + "#" + record.getRevision(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ChannelStatement findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；每 1,000 个主键一次查询，合并后按主键升序，空集合不查询。 */
    @Override
    public List<ChannelStatement> findByIds(List<Long> ids) {
        return RepositoryBatches.query(ids, mapper::selectByIds).stream()
            .sorted(Comparator.comparing(ChannelStatement::getId)).toList();
    }

    /** 读取同一清算账户 + 批次号的全部导入版本（计算 revision、检查未作废版本）。 */
    @Override
    public List<ChannelStatement> listByBatch(Long clearingAccountId, String channelBatchRef) {
        return mapper.selectByBatch(clearingAccountId, channelBatchRef);
    }

    /** 按可选条件分页读取（清算账户、批次号、状态、结算日区间）；null 条件不过滤。 */
    @Override
    public List<ChannelStatement> findPage(Long clearingAccountId, String channelBatchRef,
        ChannelStatementStatus status, LocalDate settlementDateFrom, LocalDate settlementDateTo, long offset,
        int limit) {
        return mapper.selectPage(clearingAccountId, channelBatchRef, status, settlementDateFrom, settlementDateTo,
            offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long clearingAccountId, String channelBatchRef, ChannelStatementStatus status,
        LocalDate settlementDateFrom, LocalDate settlementDateTo) {
        return mapper.countPage(clearingAccountId, channelBatchRef, status, settlementDateFrom, settlementDateTo);
    }

    /** 分段追加明细行后，在 RECEIVING 状态下按版本更新已接收行数。 */
    @Override
    public void updateImportedLineCount(ChannelStatement record, Integer importedLineCount) {
        Instant now = now();
        if (mapper.updateImportedLineCount(record, importedLineCount, now) != 1) {
            throw new ConcurrentRequestException("渠道账单已变更，请刷新后重试: " + record.getId());
        }
        record.setImportedLineCount(importedLineCount);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 按版本与原状态推进状态（完成导入、拒绝、作废、对平）；拒绝原因与对平时间按目标状态传入，其他情况传原值或 null。 */
    @Override
    public void changeStatus(ChannelStatement record, ChannelStatementStatus status, String rejectReason,
        Instant reconciledTime) {
        Instant now = now();
        if (mapper.changeStatus(record, status, rejectReason, reconciledTime, now) != 1) {
            throw new ConcurrentRequestException("渠道账单已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setRejectReason(rejectReason);
        record.setReconciledTime(reconciledTime);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 银行确认或归类结清后按版本更新到账确认金额，满额时转 CASH_CONFIRMED。 */
    @Override
    public void updateCashConfirmed(ChannelStatement record, BigDecimal cashConfirmedAmount,
        ChannelStatementStatus status) {
        Instant now = now();
        if (mapper.updateCashConfirmed(record, cashConfirmedAmount, status, now) != 1) {
            throw new ConcurrentRequestException("渠道账单已变更，请刷新后重试: " + record.getId());
        }
        record.setCashConfirmedAmount(cashConfirmedAmount);
        record.setStatus(status);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 按状态汇总在途渠道账单。 */
    @Override
    public List<ReconSummaryView.StatementTotal> sumInTransit(String entityNo, String currency) {
        return mapper.sumInTransit(entityNo, currency);
    }
}
