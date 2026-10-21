package com.paytech.reconciliation.domainservice.repository.impl;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.mapper.ReconExceptionMapper;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;

import lombok.AllArgsConstructor;

/** ReconExceptionRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ReconExceptionRepositoryImpl implements ReconExceptionRepository {
    private final Clock clock;
    private final ReconExceptionMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ReconException record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("同一对象同一类型已有未结异常: " + record.getSourceType().getCode() + "/" + record.getSourceId() + "/" + record.getExceptionType().getCode(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ReconException findById(Long id) {
        return mapper.selectById(id);
    }

    /** 读取同一对象同一类型的未结异常（OPEN、UNDER_REVIEW）。 */
    @Override
    public ReconException findActive(ReconExceptionSourceType sourceType, Long sourceId,
        ReconExceptionType exceptionType) {
        return mapper.selectActive(sourceType, sourceId, exceptionType);
    }

    /** 读取同一对象同一类型最近开立的一条异常（任意状态）。 */
    @Override
    public ReconException findLatestBySource(ReconExceptionSourceType sourceType, Long sourceId,
        ReconExceptionType exceptionType) {
        return mapper.selectLatestBySource(sourceType, sourceId, exceptionType);
    }

    /** 按主键批量插入；每 1,000 条一条多行插入，业务唯一键冲突转为并发冲突（409）。 */
    @Override
    public void insertAll(List<ReconException> records) {
        try {
            RepositoryBatches.partition(records).forEach(mapper::insertBatch);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("同一对象同一类型已有未结异常，批量开立 " + records.size() + " 条", e);
        }
    }

    /** 读取一组同类型对象上同一类型的未结异常；每 1,000 个对象一次查询。 */
    @Override
    public List<ReconException> listActiveBySources(ReconExceptionSourceType sourceType, Collection<Long> sourceIds,
        ReconExceptionType exceptionType) {
        return RepositoryBatches.query(sourceIds, part -> mapper.selectActiveBySources(sourceType, part, exceptionType));
    }

    /** 读取一组同类型对象上同一类型各自最近开立的一条异常；每 1,000 个对象一次查询。 */
    @Override
    public List<ReconException> listLatestBySources(ReconExceptionSourceType sourceType, Collection<Long> sourceIds,
        ReconExceptionType exceptionType) {
        return RepositoryBatches.query(sourceIds, part -> mapper.selectLatestBySources(sourceType, part, exceptionType));
    }

    /** 读取一张渠道账单的未结异常（对平门禁、作废时自动结案）。 */
    @Override
    public List<ReconException> listActiveByStatement(Long statementId) {
        return mapper.selectActiveByStatement(statementId);
    }

    /** 按可选条件分页读取（状态集合、异常类型、对账层、清算账户、银行账户、渠道账单）；null 条件不过滤。 */
    @Override
    public List<ReconException> findPage(List<ReconExceptionStatus> statuses, ReconExceptionType exceptionType,
        ReconLayer reconLayer, Long clearingAccountId, Long cashAccountId, Long statementId, long offset, int limit) {
        return mapper.selectPage(statuses, exceptionType, reconLayer, clearingAccountId, cashAccountId, statementId,
            offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(List<ReconExceptionStatus> statuses, ReconExceptionType exceptionType,
        ReconLayer reconLayer, Long clearingAccountId, Long cashAccountId, Long statementId) {
        return mapper.countPage(statuses, exceptionType, reconLayer, clearingAccountId, cashAccountId, statementId);
    }

    /** 按版本与原状态推进状态；OPEN、UNDER_REVIEW 占用活动槽位 1，RESOLVED、AUTO_CLOSED 置空并写结案时间。 */
    @Override
    public void changeStatus(ReconException record, ReconExceptionStatus status, Instant closedTime) {
        Instant now = now();
        Integer activeSlot = status == ReconExceptionStatus.OPEN || status == ReconExceptionStatus.UNDER_REVIEW ? 1 : null;
        if (mapper.changeStatus(record, status, activeSlot, closedTime, now) != 1) {
            throw new ConcurrentRequestException("对账异常已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setActiveSlot(activeSlot);
        record.setClosedTime(closedTime);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 按版本与原状态把多条异常推进到同一状态；每 1,000 条一条条件更新，命中行数不符即并发冲突，全部成功后同步对象。 */
    @Override
    public void changeStatusAll(List<ReconException> records, ReconExceptionStatus status, Instant closedTime) {
        if (records.isEmpty()) {
            return;
        }
        Instant now = now();
        Integer activeSlot = status == ReconExceptionStatus.OPEN || status == ReconExceptionStatus.UNDER_REVIEW ? 1 : null;
        for (List<ReconException> part : RepositoryBatches.partition(records)) {
            if (mapper.changeStatusBatch(part, status, activeSlot, closedTime, now) != part.size()) {
                throw new ConcurrentRequestException("对账异常已变更，请刷新后重试: " + part.getFirst().getId() + " 起 "
                    + part.size() + " 条");
            }
        }
        for (ReconException record : records) {
            record.setStatus(status);
            record.setActiveSlot(activeSlot);
            record.setClosedTime(closedTime);
            record.setVersion(record.getVersion() + 1);
            record.setUpdateTime(now);
        }
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 按账龄段汇总未结异常。 */
    @Override
    public List<ReconSummaryView.ExceptionAgeTotal> sumOpenByAge(String entityNo, String currency, Instant now) {
        return mapper.sumOpenByAge(entityNo, currency, now);
    }
}
