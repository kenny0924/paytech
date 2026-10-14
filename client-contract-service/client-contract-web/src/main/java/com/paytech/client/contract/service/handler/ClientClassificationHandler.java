package com.paytech.client.contract.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.contract.domain.onboarding.ClientClassificationContext;
import com.paytech.client.contract.domainservice.service.ClientClassificationSourceService;
import com.paytech.client.contract.integration.ClientReviewRegistrationClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * O04 确认后把本任务分类交给 Risk（I02）
 * <li>eventKey 是 Risk 主审核任务 ID；Risk 只接受完全相同的重放，结果未知时按原集合重试。</li>
 * <li>申请已不在本任务审核中（退回后重提等）时无事可做；Risk 任务已结束同样视为完成。</li>
 */
public final class ClientClassificationHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CLIENT_REVIEW_CLASSIFICATION";

    private final ClientReviewRegistrationClient risk;
    private final ClientClassificationSourceService classifications;

    /** 只依赖本地分类读取与 Risk 出站端口。 */
    public ClientClassificationHandler(ClientReviewRegistrationClient risk, ClientClassificationSourceService classifications) {
        this.risk = risk;
        this.classifications = classifications;
    }

    /** 纯工厂：只能与分类确认在同一本地事务登记。 */
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

    /** 每次尝试 25 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(25), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }

    /**
     * 交付分类
     * <li>Risk 明确拒绝（集合不一致等）为终态失败，保留任务供人工处理；任务已结束视为完成；其他异常可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CLIENT_CLASSIFICATION_TRANSACTION_ACTIVE", "Classification delivery must run outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("CLIENT_CLASSIFICATION_SOURCE_INVALID", "Classification event is malformed");
        }
        try {
            ClientClassificationContext source = classifications.deliverySource(Long.parseLong(event.eventKey()));
            if (source != null) {
                risk.deliverClassifications(source);
            }
            return CompensationHandlerResult.success();
        } catch (FailException rejected) {
            if ("CLIENT_REVIEW_CLOSED".equals(rejected.getCode())) {
                return CompensationHandlerResult.success();
            }
            return CompensationHandlerResult.terminalFailure("CLIENT_CLASSIFICATION_REJECTED", "Risk rejected the classification delivery");
        } catch (RuntimeException unavailable) {
            // 不把上游正文或 SQL 信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("CLIENT_CLASSIFICATION_UNAVAILABLE", "Classification delivery requires recovery");
        }
    }
}
