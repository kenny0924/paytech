package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialChange;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialQueryResult;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientApiCredentialRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientApiCredentialService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientApiCredentialRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助撤销 API 凭证
 * <li>锁 api-credential:{credentialId}，与运营紧急撤销同一互斥域；撤销对之后的新请求立即生效。</li>
 * <li>不属于本工作区返回 404；已撤销直接返回原结果、不重复审计；版本不符返回 409。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RevokeClientApiCredentialProcessor
    extends AbstractLockProcessor<ClientApiCredentialRequest, ClientApiCredentialQueryResult> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final ClientWorkspaceAccessSupport access;
    private final ClientApiCredentialService service;
    private final ClientApiCredentialRepository credentials;
    private final RedisResourceLockProperties lockProperties;

    @Override
    protected LockCommand lockCommand(ClientApiCredentialRequest request) {
        return LockCommand.tryLock("api-credential:" + request.getCredentialId(),
            Math.toIntExact(lockProperties.getWaitTime().toMillis()),
            Math.toIntExact(lockProperties.getLeaseTime().toMillis()));
    }

    @Override
    protected ClientApiCredentialQueryResult processWithinLock(ClientApiCredentialRequest request) {
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientDeveloperPermissions.API_CREDENTIAL_MANAGE);
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                var credential = credentials.lock(workspace.getTenantId(), request.getCredentialId(), now);
                if (credential == null) {
                    throw new FailException(CommonErrorCode.NOT_FOUND);
                }
                if (credential.getStoredStatus() == ApiCredentialStatus.REVOKED) {
                    audit.suppressReplay();
                    return new ClientApiCredentialQueryResult(java.util.List.of(credential), now);
                }
                if (credential.getVersion() != request.getExpectedVersion()) {
                    throw new ConcurrentRequestException("凭证已被其他操作修改，请刷新后重试");
                }
                service.revoke(new ClientApiCredentialChange(workspace, null, null, java.util.List.of(), credential, 0, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("API_CREDENTIAL", Long.toString(credential.getCredentialId())), "REVOKED", now));
                return new ClientApiCredentialQueryResult(java.util.List.of(credential), now);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }
}
