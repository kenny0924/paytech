package com.paytech.compliance.service.handler;

import java.time.Duration;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.domain.ClientReviewResult;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.QualificationDecisionRepository;
import com.paytech.compliance.integration.ClientReviewResultPublisher;
import com.paytech.compliance.service.processor.RegisterClientReviewProcessor;

import lombok.AllArgsConstructor;

/**
 * 审核结论提交后把结果发布给 CCT
 * <li>eventKey 是主任务 ID；Handler 从任务表与最终决定重建结果，补偿记录不保存业务内容。</li>
 * <li>发布收到 Broker 确认才成功；CCT 按提交编号与主任务 ID 防重，重复发布无副作用。</li>
 */
@Component
@AllArgsConstructor
public class ClientReviewResultHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CLIENT_REVIEW_RESULT";

    private static final Set<String> FINAL_STATUSES = Set.of("RETURNED", "APPROVED", "REJECTED");

    private final DueDiligenceCaseRepository cases;
    private final ClientReviewResultPublisher publisher;
    private final QualificationDecisionRepository decisions;

    /** 纯工厂：只能与主任务结束在同一本地事务登记。 */
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
     * <li>任务不存在、不是 Client 入驻主任务、尚未结束，或通过/拒绝缺少最终决定，为终态失败，保留记录供人工处理。</li>
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
        if (row == null || !RegisterClientReviewProcessor.NAMESPACE.equals(row.getSourceNamespace()) || !"CLIENT".equals(row.getRoleType())
            || !FINAL_STATUSES.contains(row.getStatus())) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_RESULT_INVALID", "Review case is not a closed client review");
        }
        QualificationDecision decision = "RETURNED".equals(row.getStatus()) ? null : decisions.findByCase(row.getCaseId());
        if (!"RETURNED".equals(row.getStatus()) && decision == null) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_RESULT_INVALID", "Review decision is missing");
        }
        try {
            publisher.publish(result(row, decision));
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CLIENT_REVIEW_RESULT_UNPUBLISHED", "Review result publication requires retry");
        }
    }

    /** 从已结束的主任务及其最终决定构造结果；退回没有决定。 */
    static ClientReviewResult result(DueDiligenceCase row, QualificationDecision decision) {
        return new ClientReviewResult(row.getCaseId(), row.getVersion(), row.getSourceApplicationId(), row.getSourceSubmissionId(),
            row.getStatus(), row.getReturnSection(), row.getPublicNote(), row.getDecisionTime(),
            decision == null ? null : decision.getDecisionId(), decision == null ? null : decision.getDecidedBy());
    }
}
