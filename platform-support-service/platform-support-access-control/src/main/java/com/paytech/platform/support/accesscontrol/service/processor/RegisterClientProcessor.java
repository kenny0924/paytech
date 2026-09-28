package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.IssuePlan;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerRegistrationService;
import com.paytech.platform.support.accesscontrol.integration.mail.AuthenticationEmailSender;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientEmailRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 注册 Client 邮箱账号并发送验证邮件。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RegisterClientProcessor extends AbstractLockProcessor<ClientEmailRequest, Void> {
    private final OperationsAuditService audit;
    private final AuthenticationEmailSender emailSender;
    private final PlatformTransactionManager transactionManager;
    final TransactionTemplate transaction;
    private final CustomerRegistrationService registration;
    private final ClientAuthenticationProperties properties;
    private final ClientEmailChallengePreparation preparation;

    /** 邮箱规范化、Vault 和锁键 HMAC 均在取锁与数据库事务前完成。 */
    @Override
    protected Void processRequest(ClientEmailRequest request) {
        var identity = preparation.prepareIdentity(request.getEmail(), request.getPassword());
        return super.processRequest(new ClientEmailRequest(request.getEmail(), request.getPassword(), identity));
    }

    /** 使用已解析的真实资源；同一资源的入口共用 Key。 */
    @Override
    protected LockCommand lockCommand(ClientEmailRequest request) {
        return LockCommand.tryLock("customer:email:" + request.getIdentity().resourceKey(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 保存成功才投递本次邮件；冷却、已有账号仍返回同一公开受理结果。 */
    @Override
    protected Void processWithinLock(ClientEmailRequest request) {
        IssuePlan plan = preparation.prepareIssue(request.getIdentity(), ClientChallengePurpose.SIGN_UP_EMAIL);
        if (plan == null) {
            return null;
        }
        boolean committed;
        try {
            committed = Boolean.TRUE.equals(transaction.execute(status -> {
                audit.observeTransaction();
                var context = preparation.currentIssue(plan);
                if (context == null) {
                    return false;
                }
                registration.issue(context);
                audit.record(() -> new CustomerAuthenticationOutcome(new Identity("UNKNOWN", null),
                    new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())), Result.SUCCEEDED,
                    plan.challenge().previousStatus() == ClientChallengeStatus.PENDING
                        ? "CHALLENGE_SUPERSEDED_AND_CREATED" : "CHALLENGE_CREATED",
                    context.getNow(), context.getChallenge().id(), plan.challenge().purpose().getCode(),
                    plan.newAccount(), plan.identity().passwordVerifier() != null, false));
                return true;
            }));
        } catch (DuplicateKeyException failure) {
            String message = failure.getMostSpecificCause().getMessage();
            if (message == null || !message.contains("uk_user_account_email")) {
                throw failure;
            }
            return null;
        }
        if (committed) {
            try {
                emailSender.send(plan.identity().canonicalEmail(), plan.challenge().purpose(), plan.challenge().link());
            } catch (ServiceUnavailableException ignored) {
                // 发送失败沿用同样受理响应；客户重发会替代原链接，不保存明文重试材料。
            }
        }
        return null;
    }

    /** 本库写事务保持读已提交和十五秒超时；审计观察实际提交结果。 */
    TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        return transaction;
    }
}
