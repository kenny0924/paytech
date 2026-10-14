package com.paytech.client.contract.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.contract.domain.enums.ClientApplicationStatus;
import com.paytech.client.contract.domainservice.service.ClientReviewRegistrationService;
import com.paytech.client.contract.integration.ClientReviewRegistrationClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * C14 提交后向 Risk 登记审核任务（I01），成功后申请 SUBMITTED → UNDER_REVIEW。
 * <li>eventKey 是提交编号；结果未知时用同一编号重试，Risk 按编号防重，不生成新提交。</li>
 * <li>编号已不是当前提交（例如后续退回重提）时无事可做，直接成功。</li>
 */
public final class ClientReviewRegistrationHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CLIENT_REVIEW_REGISTRATION";

    private final ClientReviewRegistrationService submissions;
    private final ClientReviewRegistrationClient risk;

    /** 只依赖本地提交服务与 Risk 出站端口。 */
    public ClientReviewRegistrationHandler(ClientReviewRegistrationService submissions, ClientReviewRegistrationClient risk) {
        this.submissions = submissions;
        this.risk = risk;
    }

    /** 纯工厂：只能与提交状态迁移在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(String submissionId) {
        if (submissionId == null || !submissionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Invalid submission identity");
        }
        return new CompensationEventRequest(submissionId, EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 25 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }

    /**
     * 登记审核任务
     * <li>Risk 明确拒绝为终态失败，保留任务供人工处理；其他异常可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_TRANSACTION_ACTIVE", "Review registration must run outside a transaction");
        }
        if (event.payloadJson() != null) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_SOURCE_INVALID", "Review registration event is malformed");
        }
        try {
            var source = submissions.reviewSource(event.eventKey());
            if (source == null || source.getApplication().getStatus() != ClientApplicationStatus.SUBMITTED) {
                return CompensationHandlerResult.success();
            }
            long caseId = risk.register(source);
            if (submissions.startReview(source.getApplication(), caseId)) {
                return CompensationHandlerResult.success();
            }
            var current = submissions.reviewSource(event.eventKey());
            if (current != null && current.getApplication().getStatus() == ClientApplicationStatus.UNDER_REVIEW
                && Long.valueOf(caseId).equals(current.getApplication().getDueDiligenceCaseId())) {
                return CompensationHandlerResult.success();
            }
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_CONFLICT", "Application changed during review registration");
        } catch (FailException rejected) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_REJECTED", "Risk rejected the review registration");
        } catch (RuntimeException unavailable) {
            // 不把上游正文或 SQL 信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CLIENT_REVIEW_UNAVAILABLE", "Review registration outcome requires recovery");
        }
    }
}
