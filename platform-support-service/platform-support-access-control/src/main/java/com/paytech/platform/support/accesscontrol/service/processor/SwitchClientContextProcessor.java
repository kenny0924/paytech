package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.SwitchPlan;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.Tokens;
import com.paytech.platform.support.accesscontrol.domain.authentication.SwitchClientContext;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientSwitchRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 切换 Client 会话上下文（PLT-I16）：进入工作区或返回账号上下文。
 * <li>总是新建会话：撤销当前会话及其刷新代，新会话绝对到期沿用当前会话。</li>
 * <li>锁当前账号与会话：与登录、租户投影（账号）及刷新、退出（会话）共用互斥域。</li>
 * <li>目标成员不存在、不属于本账号、未激活或租户不可访问时统一返回 CLIENT_WORKSPACE_UNAVAILABLE，不区分原因。</li>
 * <li>Cookie 证明、签名在事务外准备；事务内按账号、会话、刷新代、成员顺序加锁重证后提交。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class SwitchClientContextProcessor extends AbstractLockProcessor<ClientSwitchRequest, Tokens> {
    private final OperationsAuditService audit;
    private final ClientSessionPreparation preparation;
    private final PlatformTransactionManager transactionManager;
    private final CustomerAuthenticationService authentication;
    private final ClientAuthenticationProperties properties;

    /** 同时锁当前账号与会话，等待与租约沿用 Client 认证配置。 */
    @Override
    protected LockCommand lockCommand(ClientSwitchRequest request) {
        return LockCommand.tryLocks(
            List.of("customer:account:" + request.getAccountId(), "customer:session:" + request.getSessionId()),
            properties.getLockWaitTime(), properties.getLockLeaseTime());
    }

    @Override
    protected Tokens processWithinLock(ClientSwitchRequest request) {
        SwitchPlan plan = preparation.prepareSwitch(request.getAccountId(), request.getSessionId(),
            request.getRefreshToken(), request.getMemberId());
        Outcome outcome = transaction().execute(status -> {
            audit.observeTransaction();
            SwitchClientContext context = preparation.currentSwitch(plan);
            if (context == null || !preparation.switchUsable(context)) {
                return Outcome.SESSION_INVALID;
            }
            if (!preparation.targetAllowed(context)) {
                recordOutcome(context, Result.DENIED, "WORKSPACE_UNAVAILABLE");
                return Outcome.WORKSPACE_UNAVAILABLE;
            }
            preparation.requireCurrentSigningKey(plan.issue(), context.getNow());
            authentication.switchContext(context);
            recordOutcome(context, Result.SUCCEEDED, context.toAccount() ? "ACCOUNT_CONTEXT_SELECT" : "WORKSPACE_SELECT");
            return Outcome.SWITCHED;
        });
        if (outcome == Outcome.WORKSPACE_UNAVAILABLE) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        if (outcome != Outcome.SWITCHED) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID);
        }
        return plan.issue().tokens();
    }

    /** 审计目标为新会话；成功时同时记录原会话已撤销。 */
    void recordOutcome(SwitchClientContext context, Result result, String reason) {
        boolean succeeded = result == Result.SUCCEEDED;
        long sessionId = succeeded ? context.getPlan().issue().sessionId() : context.getSession().id();
        audit.record(() -> new CustomerAuthenticationOutcome(new Identity(Long.toString(context.getAccount().id()), null),
            new Target("AUTH_SESSION", Long.toString(sessionId)), result, reason, context.getNow(), null, null, false,
            false, succeeded));
    }

    /** 本库写事务保持读已提交和十五秒超时；审计观察实际提交结果。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }

    /** 事务内结论：拒绝结论随审计一同提交后再转为错误。 */
    enum Outcome {
        SWITCHED,

        SESSION_INVALID,

        WORKSPACE_UNAVAILABLE
    }
}
