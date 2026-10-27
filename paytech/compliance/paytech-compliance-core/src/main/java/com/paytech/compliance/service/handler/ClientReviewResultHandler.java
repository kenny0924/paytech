package com.paytech.compliance.service.handler;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewResult;
import com.paytech.compliance.domain.RequirementError;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.domainservice.repository.ReturnErrorRepository;
import com.paytech.compliance.integration.ClientReviewResultPublisher;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

import lombok.AllArgsConstructor;

/**
 * 审核结论提交后把结果发布给 client-master（ClientReviewDecided v2）
 * <li>eventKey 是主案件 ID；Handler 从案件、最终决定与退回错误重建结果，补偿记录不保存业务内容。</li>
 * <li>发布 RETURNED、APPROVED、REJECTED、EXPIRED；撤回由 client-master 发起，不发布。</li>
 * <li>发布收到 Broker 确认才成功；client-master 按提交编号与主案件 ID 防重，重复发布无副作用。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewResultHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CLIENT_REVIEW_RESULT";

    private static final Set<String> PUBLISHED = Set.of(DueDiligenceCaseStatus.RETURNED.getCode(),
        DueDiligenceCaseStatus.APPROVED.getCode(), DueDiligenceCaseStatus.REJECTED.getCode(), DueDiligenceCaseStatus.EXPIRED.getCode());

    private static final Set<String> DECIDED = Set.of(DueDiligenceCaseStatus.APPROVED.getCode(),
        DueDiligenceCaseStatus.REJECTED.getCode());

    private final DueDiligenceCaseRepository cases;
    private final ReturnErrorRepository returnErrors;
    private final ClientReviewResultPublisher publisher;
    private final QualificationDecisionRepository decisions;

    /** 纯工厂：只能与主案件结束在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long caseId) {
        if (caseId <= 0) {
            throw new IllegalArgumentException("Invalid review case identity");
        }
        return new CompensationEventRequest(Long.toString(caseId), EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }

    /**
     * 发布审核结果
     * <li>案件不存在、不是 Client 入驻主案件、不在发布集合，或批准 / 拒绝缺少最终决定，为终态失败，保留记录供人工处理。</li>
     * <li>Broker 未确认或连接失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_TRANSACTION_ACTIVE", "Review result must publish outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_RESULT_INVALID", "Review result event is malformed");
        }
        DueDiligenceCase row = cases.findById(Long.parseLong(event.eventKey()));
        if (row == null || !ReviewCodes.NAMESPACE_CLIENT_APPLICATION.equals(row.getSourceNamespace())
            || !ReviewCodes.ROLE_CLIENT.equals(row.getRoleType()) || !PUBLISHED.contains(row.getStatus())) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_RESULT_INVALID", "Review case is not a closed client review");
        }
        QualificationDecision decision = DECIDED.contains(row.getStatus()) ? decisions.findByCase(row.getCaseId()) : null;
        if (DECIDED.contains(row.getStatus()) && decision == null) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_RESULT_INVALID", "Review decision is missing");
        }
        try {
            publisher.publish(result(row, decision, errors(row)));
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CLIENT_REVIEW_RESULT_UNPUBLISHED", "Review result publication requires retry");
        }
    }

    /** 退回的需求级错误；其他结果为空列表。 */
    List<RequirementError> errors(DueDiligenceCase row) {
        if (!DueDiligenceCaseStatus.RETURNED.getCode().equals(row.getStatus())) {
            return List.of();
        }
        return returnErrors.findByCase(row.getCaseId()).stream().map(error -> new RequirementError(error.getRequirementKey(),
            error.getSubjectPartyId(), error.getErrorCode(), error.getReason(), error.getRemediationCode())).toList();
    }

    /** 从已结束的主案件、最终决定与退回错误构造结果。 */
    static ClientReviewResult result(DueDiligenceCase row, QualificationDecision decision, List<RequirementError> errors) {
        boolean returned = DueDiligenceCaseStatus.RETURNED.getCode().equals(row.getStatus());
        return new ClientReviewResult(row.getCaseId(), row.getVersion(), row.getSourceApplicationId(), row.getSourceSubmissionId(),
            row.getStatus(), row.getReturnSection(), row.getPublicNote(), row.getDecisionTime(),
            decision == null ? null : decision.getDecisionId(), decision == null ? null : decision.getDecidedBy(), row.getCaseType(),
            row.getRoundNo(), decision == null ? null : decision.getTier(), decision == null ? null : decision.getRiskRating(),
            returned ? row.getReviewDueTime() : null, errors);
    }
}
