package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.ClientAuthenticationProperties;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerRegistrationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientLinkRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.CustomerAuthenticationOutcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 重置 Client 密码并撤销旧会话。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ResetClientPasswordProcessor extends AbstractLockProcessor<ClientLinkRequest, Void> {
    private final OperationsAuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRegistrationService registration;
    private final ClientAuthenticationProperties properties;
    private final ClientEmailChallengePreparation preparation;

    /** 秘密 HMAC 和新密码 verifier 在事务外准备，不允许无效秘密触发邮箱 Reveal。 */
    @Override
    protected Void processRequest(ClientLinkRequest request) {
        var proof = preparation.prepareProof(request.getChallengeNo(), request.getSecret(),
            ClientChallengePurpose.PASSWORD_RESET, request.getNewPassword());
        if (proof == null) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_EMAIL_LINK_INVALID);
        }
        return super.processRequest(
            new ClientLinkRequest(request.getChallengeNo(), request.getSecret(), request.getNewPassword(), proof));
    }

    /** 使用已解析的真实资源；同一资源的入口共用 Key。 */
    @Override
    protected LockCommand lockCommand(ClientLinkRequest request) {
        return LockCommand.tryLock("customer:account:" + request.getProof().accountId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 失败尝试也必须先提交，再向调用方返回统一的无效链接错误。 */
    @Override
    protected Void processWithinLock(ClientLinkRequest request) {
        boolean accepted = Boolean.TRUE.equals(transaction().execute(status -> {
            audit.observeTransaction();
            var context = preparation.currentProof(request.getProof());
            if (context == null) {
                return false;
            }
            if (!preparation.accepts(context)) {
                if (context.getChallenge().status() == ClientChallengeStatus.PENDING) {
                    registration.denyProof(context);
                    audit.record(() -> new CustomerAuthenticationOutcome(new Identity("UNKNOWN", null),
                        new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())), Result.DENIED,
                        context.getNow().isBefore(context.getChallenge().expiresTime()) ? "CHALLENGE_PROOF_DENIED"
                            : "CHALLENGE_EXPIRED",
                        context.getNow(), context.getChallenge().id(), request.getProof().purpose().getCode(), false,
                        false, false));
                }
                return false;
            }
            registration.resetPassword(context);
            audit.record(
                () -> new CustomerAuthenticationOutcome(new Identity(Long.toString(context.getAccount().id()), null),
                    new Target("USER_ACCOUNT", Long.toString(context.getAccount().id())), Result.SUCCEEDED,
                    "PASSWORD_RESET", context.getNow(), context.getChallenge().id(),
                    request.getProof().purpose().getCode(), false, true, true));
            return true;
        }));
        if (!accepted) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_EMAIL_LINK_INVALID);
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
