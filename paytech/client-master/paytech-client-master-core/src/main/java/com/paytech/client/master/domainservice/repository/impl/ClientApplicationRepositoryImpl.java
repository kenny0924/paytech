package com.paytech.client.master.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientWorkspaceSummary;
import com.paytech.client.master.core.dal.mapper.ClientApplicationMapper;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** 申请提交坐标的条件更新；SQL 成功后同步原对象。 */
@AllArgsConstructor
public class ClientApplicationRepositoryImpl implements ClientApplicationRepository {
    private final ClientApplicationMapper mapper;

    /** 按申请和发起账号读取。 */
    @Override
    public ClientApplicationSubmission findByApplication(long applicationId, long accountId) {
        return mapper.findByApplication(applicationId, accountId);
    }

    /** 按提交编号读取。 */
    @Override
    public ClientApplicationSubmission findBySubmission(String submissionId) {
        return mapper.findBySubmission(submissionId);
    }

    /** 条件迁移为 SUBMITTED。 */
    @Override
    public void submit(ClientApplicationSubmission application, String submissionId, Instant now) {
        if (mapper.submit(application.getApplicationId(), submissionId, now) != 1) {
            throw new ConcurrentRequestException("申请状态已变化，请刷新后重试");
        }
        application.setStatus(ClientApplicationStatus.SUBMITTED);
        application.setCurrentSubmissionId(submissionId);
        application.setPreviousCaseId(application.getDueDiligenceCaseId());
        application.setDueDiligenceCaseId(null);
        application.setSubmissionRound(application.getSubmissionRound() + 1);
        application.setReviewDueTime(null);
        application.setSubmittedTime(now);
        application.setUpdateTime(now);
    }

    /** 条件迁移为 UNDER_REVIEW。 */
    @Override
    public boolean startReview(ClientApplicationSubmission application, long caseId, Instant now) {
        if (mapper.startReview(application.getApplicationId(), application.getCurrentSubmissionId(), caseId, now) != 1) {
            return false;
        }
        application.setStatus(ClientApplicationStatus.UNDER_REVIEW);
        application.setDueDiligenceCaseId(caseId);
        application.setUpdateTime(now);
        return true;
    }

    /** 按申请读取。 */
    @Override
    public ClientApplicationSubmission findById(long applicationId) {
        return mapper.findById(applicationId);
    }

    /** Client 的全部申请，新在前。 */
    @Override
    public List<ClientApplicationSubmission> findByClient(long clientId) {
        return List.copyOf(mapper.findByClient(clientId));
    }

    /** 按当前审核任务读取。 */
    @Override
    public ClientApplicationSubmission findByCase(long caseId) {
        return mapper.findByCase(caseId);
    }

    /** 条件结束审核。 */
    @Override
    public void concludeReview(ClientApplicationSubmission application, ClientApplicationStatus status, Long decisionId, Instant dueTime,
        Instant now) {
        ClientApplicationStatus expected = status == ClientApplicationStatus.WITHDRAWN ? ClientApplicationStatus.RETURNED
            : ClientApplicationStatus.UNDER_REVIEW;
        if (mapper.concludeReview(application, expected, status, decisionId, dueTime, status != ClientApplicationStatus.RETURNED,
            now) != 1) {
            throw new ConcurrentRequestException("申请审核状态已变化");
        }
        application.setStatus(status);
        application.setQualificationDecisionId(decisionId);
        application.setReviewDueTime(dueTime);
        application.setUpdateTime(now);
    }

    /** 条件撤回，成功后同步原对象。 */
    @Override
    public boolean withdraw(ClientApplicationSubmission application, Instant now) {
        if (mapper.withdraw(application.getApplicationId(), now) != 1) {
            return false;
        }
        application.setStatus(ClientApplicationStatus.WITHDRAWN);
        application.setUpdateTime(now);
        return true;
    }

    /** 只读组合查询。 */
    @Override
    public List<ClientWorkspaceSummary> findApprovedSummaries(List<Long> clientIds) {
        return mapper.findApprovedSummaries(clientIds);
    }
}
