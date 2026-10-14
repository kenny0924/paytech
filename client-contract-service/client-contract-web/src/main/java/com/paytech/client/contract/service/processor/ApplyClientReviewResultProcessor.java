package com.paytech.client.contract.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.client.contract.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.contract.core.dal.dataobject.ClientProjectionHistory;
import com.paytech.client.contract.domain.enums.BusinessChangeOperation;
import com.paytech.client.contract.domain.enums.BusinessChangeSnapshotFormat;
import com.paytech.client.contract.domain.enums.ClientApplicationStatus;
import com.paytech.client.contract.domain.enums.ClientStatus;
import com.paytech.client.contract.domain.onboarding.ClientReviewOutcomeContext;
import com.paytech.client.contract.domainservice.ClientProjectionSnapshot;
import com.paytech.client.contract.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.contract.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.contract.domainservice.service.ClientReviewOutcomeService;
import com.paytech.client.contract.service.processor.request.ApplyClientReviewResultRequest;
import com.paytech.client.contract.service.processor.response.ApplyClientReviewResultResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 落地 Risk 审核结果（RETURNED/APPROVED/REJECTED）；由 MQ 监听器调用
 * <li>按申请 + 提交编号 + 主任务 ID 核对，旧任务的迟到结果忽略；同一结果重复投递不重复执行。</li>
 * <li>通过时同一事务激活 Client、追加 CLIENT_ACTIVATED 生命周期历史并登记 Platform 工作区投影任务。</li>
 * <li>不加 Redis 锁：申请状态、Client 状态与历史根版本均由条件更新保护，冲突整体回滚后由消息重投处理。</li>
 */
@AllArgsConstructor
public class ApplyClientReviewResultProcessor extends AbstractProcessor<ApplyClientReviewResultRequest, ApplyClientReviewResultResponse> {
    private final Clock clock;
    private final IdGenerator ids;
    private final ClientReviewOutcomeService outcomes;
    private final ClientProjectionRepository projections;
    private final ClientApplicationRepository applications;

    /** 通过与拒绝必须携带最终决定；通过还须有决定人。 */
    @Override
    protected void validate(ApplyClientReviewResultRequest request) {
        if (!"RETURNED".equals(request.getOutcome()) && (request.getDecisionId() == null || request.getDecisionId() <= 0)) {
            throw new BizCheckFailException("审核结果缺少最终决定: " + request.getCaseId());
        }
        if ("APPROVED".equals(request.getOutcome()) && (request.getDecidedBy() == null || request.getDecidedBy() <= 0)) {
            throw new BizCheckFailException("审核通过缺少决定人: " + request.getCaseId());
        }
    }

    @Override
    protected ApplyClientReviewResultResponse processRequest(ApplyClientReviewResultRequest request) {
        // 当前提交与任务
        ClientApplicationSubmission application = applications.findById(request.getApplicationId());
        if (application == null) {
            throw new BizCheckFailException("审核结果对应的申请不存在: " + request.getApplicationId());
        }
        if (!request.getSubmissionId().equals(application.getCurrentSubmissionId())) {
            return ApplyClientReviewResultResponse.STALE;
        }
        ClientApplicationStatus target = target(request.getOutcome());
        if (application.getDueDiligenceCaseId() == null) {
            // 审核登记结果尚未落库；消息重投后再处理。
            throw new ErrorException("申请尚未记录审核任务: " + request.getApplicationId());
        }
        if (application.getDueDiligenceCaseId() != request.getCaseId()) {
            return ApplyClientReviewResultResponse.STALE;
        }
        if (application.getStatus() == target) {
            return ApplyClientReviewResultResponse.DUPLICATE;
        }
        if (application.getStatus() != ClientApplicationStatus.UNDER_REVIEW) {
            return ApplyClientReviewResultResponse.STALE;
        }

        // 保存上下文
        outcomes.conclude(buildContext(request, application, target));
        return ApplyClientReviewResultResponse.APPLIED;
    }

    /** 结果代码到申请状态；代码已由请求注解限定为三种。 */
    static ClientApplicationStatus target(String outcome) {
        return switch (outcome) {
            case "RETURNED" -> ClientApplicationStatus.RETURNED;
            case "APPROVED" -> ClientApplicationStatus.APPROVED;
            default -> ClientApplicationStatus.REJECTED;
        };
    }

    /** 通过时从最新生命周期历史构造下一版 ACTIVE 历史；Client 必须仍为 PENDING。 */
    ClientReviewOutcomeContext buildContext(ApplyClientReviewResultRequest request, ClientApplicationSubmission application,
        ClientApplicationStatus target) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (target != ClientApplicationStatus.APPROVED) {
            return new ClientReviewOutcomeContext(application, target, request.getDecisionId(), null, null, now);
        }
        ClientProjectionHistory latest = projections.latest(application.getClientId());
        if (latest == null || !ClientStatus.PENDING.getCode().equals(ClientProjectionSnapshot.read(latest).status())) {
            throw new ErrorException("Client 生命周期不是 PENDING: " + application.getClientId());
        }
        ClientProjectionHistory next = new ClientProjectionHistory(ids.nextId(), latest.historyId(), latest.recordId(),
            latest.sourceVersion() + 1, BusinessChangeOperation.UPDATE, BusinessChangeSnapshotFormat.ENTITY_NO, null,
            request.getDecidedBy(), now, latest.clientId(), latest.partyId(), latest.clientNo(), latest.applicantId(),
            latest.applicationCount());
        String snapshot = ClientProjectionSnapshot.snapshot(next, ClientStatus.ACTIVE, Long.toString(request.getDecisionId()),
            "CLIENT_ACTIVATED");
        return new ClientReviewOutcomeContext(application, target, request.getDecisionId(), next, snapshot, now);
    }
}
