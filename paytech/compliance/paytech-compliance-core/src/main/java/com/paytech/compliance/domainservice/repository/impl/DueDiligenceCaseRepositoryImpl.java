package com.paytech.risk.compliance.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import com.paytech.risk.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.risk.compliance.core.dal.mapper.DueDiligenceCaseMapper;
import com.paytech.risk.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** t_due_diligence_case 单表持久化。 */
@Repository
@AllArgsConstructor
public class DueDiligenceCaseRepositoryImpl implements DueDiligenceCaseRepository {
    private final DueDiligenceCaseMapper mapper;

    /** 新增案件。 */
    @Override
    public void insert(DueDiligenceCase row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("案件写入行数异常");
        }
    }

    /** 同一来源提交的全部案件。 */
    @Override
    public List<DueDiligenceCase> findBySubmission(String namespace, long applicationId, String submissionId) {
        return mapper.findBySubmission(namespace, applicationId, submissionId);
    }

    /** 按案件 ID 读取。 */
    @Override
    public DueDiligenceCase findById(long caseId) {
        return mapper.findById(caseId);
    }

    /** 按案件 ID 集合读取。 */
    @Override
    public List<DueDiligenceCase> findByIds(List<Long> caseIds) {
        return caseIds.isEmpty() ? List.of() : mapper.findByIds(caseIds);
    }

    /** Client 入驻主案件分页。 */
    @Override
    public List<DueDiligenceCase> pageClientCases(String status, String qualificationType, Long applicationId, long offset, int size) {
        return mapper.pageClientCases(status, qualificationType, applicationId, offset, size);
    }

    /** Client 入驻主案件计数。 */
    @Override
    public long countClientCases(String status, String qualificationType, Long applicationId) {
        return mapper.countClientCases(status, qualificationType, applicationId);
    }

    /** 条件结束案件并同步原对象。 */
    @Override
    public void close(DueDiligenceCase row, String status, String publicNote, String resolutionNote, String returnSection, Instant now) {
        if (mapper.close(row, status, publicNote, resolutionNote, returnSection, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("审核任务已变化，请刷新后重试");
        }
        row.setStatus(status);
        row.setActiveSlot(null);
        row.setPublicNote(publicNote);
        row.setResolutionNote(resolutionNote);
        row.setReturnSection(returnSection);
        row.setDecisionTime(now);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }

    /** 条件递增版本并同步原对象。 */
    @Override
    public void touch(DueDiligenceCase row, Instant now) {
        if (mapper.touch(row, now) != 1) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("审核任务已变化，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}
