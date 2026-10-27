package com.paytech.client.master.service.handler;

import java.time.Duration;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domainservice.service.ClientReviewRegistrationService;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.compliance.api.enums.ClientReviewErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * Client 撤回已立案的申请后通知 compliance 撤回审核案件（I03）
 * <li>eventKey 是申请 ID；Handler 实时读取申请当前绑定的审核任务，载荷为空，不含客户资料。</li>
 * <li>compliance 已撤回为幂等成功；案件已结束（CLIENT_REVIEW_CLOSED，例如退回已超期）视为无事可做；其他明确拒绝为终态，网络或依赖失败可重试。</li>
 */
public final class ClientReviewWithdrawalHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "CLIENT_REVIEW_WITHDRAWAL";

    private final ClientReviewRegistrationService applications;
    private final ClientReviewRegistrationClient compliance;

    /** 只依赖本地申请读取与 compliance 出站端口；不依赖补偿登记器，避免与 Handler 注册表形成循环。 */
    public ClientReviewWithdrawalHandler(ClientReviewRegistrationService applications, ClientReviewRegistrationClient compliance) {
        this.applications = applications;
        this.compliance = compliance;
    }

    /** 纯工厂：只能与撤回状态迁移在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long applicationId) {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("Invalid application identity");
        }
        return new CompensationEventRequest(Long.toString(applicationId), EVENT_TYPE, "-");
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

    /** 读取撤回事实并调用 compliance I03。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_TRANSACTION_ACTIVE", "Withdrawal must run outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_SOURCE_INVALID", "Withdrawal event is malformed");
        }
        try {
            var application = applications.withdrawalSource(Long.parseLong(event.eventKey()));
            if (application == null || application.getStatus() != ClientApplicationStatus.WITHDRAWN
                || application.getDueDiligenceCaseId() == null) {
                return CompensationHandlerResult.success();
            }
            compliance.withdraw(application.getDueDiligenceCaseId(), application.getApplicationId(), application.getAccountId());
            return CompensationHandlerResult.success();
        } catch (FailException rejected) {
            if (ClientReviewErrorCode.CLIENT_REVIEW_CLOSED.getCode().equals(rejected.getCode())) {
                return CompensationHandlerResult.success();
            }
            return CompensationHandlerResult.terminalFailure("CLIENT_REVIEW_WITHDRAWAL_REJECTED", "compliance rejected the withdrawal");
        } catch (RuntimeException unavailable) {
            return CompensationHandlerResult.retryableFailure("CLIENT_REVIEW_UNAVAILABLE", "Withdrawal outcome requires recovery");
        }
    }
}
