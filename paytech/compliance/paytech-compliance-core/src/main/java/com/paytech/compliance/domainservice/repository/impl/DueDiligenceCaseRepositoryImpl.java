package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.mapper.DueDiligenceCaseMapper;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.domain.CaseClosing;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 尽调案件持久化：条件更新确认行数后同步原对象。 */
@Repository
@AllArgsConstructor
public class DueDiligenceCaseRepositoryImpl implements DueDiligenceCaseRepository {
    private static final String CHANGED = "审核案件已变化，请刷新后重试";

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

    /** 最近一轮主案件。 */
    @Override
    public DueDiligenceCase findLatestClientCase(long applicationId) {
        return mapper.findLatestClientCase(applicationId);
    }

    /** 是否已有后继。 */
    @Override
    public boolean hasSuccessor(long caseId) {
        return mapper.countSuccessors(caseId) > 0;
    }

    /** 到期退回案件。 */
    @Override
    public List<DueDiligenceCase> findDueReturned(Instant now, int limit) {
        return mapper.findDueReturned(now, limit);
    }

    /** 主案件分页。 */
    @Override
    public List<DueDiligenceCase> pageClientCases(String status, String qualificationType, Long applicationId, Long responsiblePartnerId,
        boolean platformOnly, String jurisdictionCode, long offset, int size) {
        return mapper.pageClientCases(status, qualificationType, applicationId, responsiblePartnerId, platformOnly, jurisdictionCode,
            offset, size);
    }

    /** 主案件计数。 */
    @Override
    public long countClientCases(String status, String qualificationType, Long applicationId, Long responsiblePartnerId,
        boolean platformOnly, String jurisdictionCode) {
        return mapper.countClientCases(status, qualificationType, applicationId, responsiblePartnerId, platformOnly, jurisdictionCode);
    }

    /** 结束进行中案件并同步原对象。 */
    @Override
    public void close(DueDiligenceCase row, String status, CaseClosing closing, Instant now) {
        if (mapper.close(row, status, closing.getPublicNote(), closing.getResolutionNote(), closing.getReturnSection(),
            closing.getReviewDueTime(), closing.getOperatedBy(), closing.getOnBehalfOfPartnerId(), now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        row.setStatus(status);
        row.setActiveSlot(null);
        row.setPublicNote(closing.getPublicNote());
        row.setResolutionNote(closing.getResolutionNote());
        row.setReturnSection(closing.getReturnSection());
        row.setReviewDueTime(closing.getReviewDueTime());
        row.setOperatedBy(closing.getOperatedBy());
        row.setOnBehalfOfPartnerId(closing.getOnBehalfOfPartnerId());
        row.setDecisionTime(now);
        bump(row, now);
    }

    /** 迁移到撤回或过期并同步原对象。 */
    @Override
    public void transition(DueDiligenceCase row, String toStatus, Instant now) {
        if (mapper.transition(row, toStatus, now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        row.setStatus(toStatus);
        row.setActiveSlot(null);
        row.setDecisionTime(now);
        bump(row, now);
    }

    /** 递增版本。 */
    @Override
    public void touch(DueDiligenceCase row, Instant now) {
        if (mapper.touch(row, now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        bump(row, now);
    }

    /** 开始审核。 */
    @Override
    public void startReview(DueDiligenceCase row, Instant now) {
        if (mapper.startReview(row, now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        row.setStatus(DueDiligenceCaseStatus.IN_REVIEW.getCode());
        bump(row, now);
    }

    /** 手工评级。 */
    @Override
    public void updateRiskRating(DueDiligenceCase row, String riskRating, String note, long actorId, Instant now) {
        if (mapper.updateRiskRating(row, riskRating, note, actorId, now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        row.setRiskRating(riskRating);
        row.setRiskRatingNote(note);
        row.setRiskRatedBy(actorId);
        row.setRiskRatedTime(now);
        row.setRecommendation(null);
        row.setRecommendationNote(null);
        row.setRecommendedBy(null);
        row.setRecommendedTime(null);
        bump(row, now);
    }

    /** 推荐。 */
    @Override
    public void updateRecommendation(DueDiligenceCase row, String recommendation, String note, long actorId, Instant now) {
        if (mapper.updateRecommendation(row, recommendation, note, actorId, now) != 1) {
            throw new ConcurrentRequestException(CHANGED);
        }
        row.setRecommendation(recommendation);
        row.setRecommendationNote(note);
        row.setRecommendedBy(actorId);
        row.setRecommendedTime(now);
        bump(row, now);
    }

    /** 同步版本与更新时间。 */
    static void bump(DueDiligenceCase row, Instant now) {
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}
