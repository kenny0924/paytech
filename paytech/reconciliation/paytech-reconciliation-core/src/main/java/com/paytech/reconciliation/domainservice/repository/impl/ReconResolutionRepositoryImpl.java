package com.paytech.reconciliation.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.core.dal.mapper.ReconResolutionMapper;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;

import lombok.AllArgsConstructor;

/** ReconResolutionRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ReconResolutionRepositoryImpl implements ReconResolutionRepository {
    private final Clock clock;
    private final ReconResolutionMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(ReconResolution record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("异常处置序号已存在: exceptionId=" + record.getExceptionId() + ", no=" + record.getResolutionNo(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ReconResolution findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；空集合不查询。 */
    @Override
    public List<ReconResolution> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 读取一个异常的全部处置（处置历史、下一序号）。 */
    @Override
    public List<ReconResolution> listByException(Long exceptionId) {
        return mapper.selectByException(exceptionId);
    }

    /** 按可选条件分页读取（处置状态）；null 条件不过滤。 */
    @Override
    public List<ReconResolution> findPage(ResolutionStatus status, long offset, int limit) {
        return mapper.selectPage(status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(ResolutionStatus status) {
        return mapper.countPage(status);
    }

    /** 按版本把 PENDING_CHECK 处置转为 APPLIED（批准即应用）或 REJECTED。 */
    @Override
    public void review(ReconResolution record, ResolutionStatus status, Long checkerId,
        CheckerDecision checkerDecision, String approvedContentHash, Instant decisionTime) {
        Instant now = now();
        if (mapper.review(record, status, checkerId, checkerDecision, approvedContentHash, decisionTime, now) != 1) {
            throw new ConcurrentRequestException("异常处置已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setCheckerId(checkerId);
        record.setCheckerDecision(checkerDecision);
        record.setApprovedContentHash(approvedContentHash);
        record.setDecisionTime(decisionTime);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
