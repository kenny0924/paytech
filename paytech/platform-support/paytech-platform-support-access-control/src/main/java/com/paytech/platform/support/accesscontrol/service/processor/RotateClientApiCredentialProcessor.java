package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientApiCredential;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialChange;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialResult;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientApiCredentialRules;
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
 * 工作区成员自助轮换 API 凭证
 * <li>锁旧凭证 api-credential:{credentialId}，与运营紧急撤销同一互斥域；轮换不受 10 个上限阻止。</li>
 * <li>新凭证继承名称、当前有效 Scope 与有效期天数；旧凭证宽限 0 立即撤销，否则到期时间提前到宽限截止。</li>
 * <li>旧凭证不属于本工作区返回 404；已撤销或已到期不能轮换；版本不符返回 409。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RotateClientApiCredentialProcessor
    extends AbstractLockProcessor<ClientApiCredentialRequest, ClientApiCredentialResult> {
    private final Clock clock;
    private final IdGenerator ids;
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
    protected ClientApiCredentialResult processWithinLock(ClientApiCredentialRequest request) {
        int grace = ClientApiCredentialRules.graceMinutes(request.getGraceMinutes());
        var secret = ClientApiCredentialRules.generate();
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientDeveloperPermissions.API_CREDENTIAL_MANAGE);
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                var original = requireRotatable(credentials.lock(workspace.getTenantId(), request.getCredentialId(), now),
                    request.getExpectedVersion(), now);
                var replacement = replacement(original, secret.keyPrefix(), now);
                var change = new ClientApiCredentialChange(workspace, replacement, secret.verifierPhc(),
                    replacement.getScopeCodes().stream().map(code -> ids.nextId()).toList(), original, grace, now);
                service.rotate(change);
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("API_CREDENTIAL", Long.toString(original.getCredentialId())), "ROTATED_TO_"
                        + replacement.getCredentialId(), now));
                return new ClientApiCredentialResult(replacement, secret.credential(), now);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 只有本工作区、当前有效、版本一致且仍有 Scope 的凭证可以轮换。 */
    ClientApiCredential requireRotatable(ClientApiCredential original, int expectedVersion, java.time.Instant now) {
        if (original == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (original.effectiveStatus(now) != ApiCredentialStatus.ACTIVE) {
            throw new BizCheckFailException("凭证已撤销或已到期，不能轮换，请新建凭证");
        }
        if (original.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("凭证已被其他操作修改，请刷新后重试");
        }
        if (original.getScopeCodes().isEmpty()) {
            throw new BizCheckFailException("凭证当前没有有效 Scope，不能轮换，请新建凭证");
        }
        return original;
    }

    /** 新凭证继承名称、Scope 与有效期天数。 */
    ClientApiCredential replacement(ClientApiCredential original, String keyPrefix, java.time.Instant now) {
        int days = ClientApiCredentialRules.inheritedDays(original.getCreateTime(), original.getExpiresTime());
        var credential = new ClientApiCredential();
        credential.setCredentialId(ids.nextId());
        credential.setTenantId(original.getTenantId());
        credential.setKeyPrefix(keyPrefix);
        credential.setCredentialName(original.getCredentialName());
        credential.setExpiresTime(now.plus(Duration.ofDays(days)));
        credential.setStoredStatus(ApiCredentialStatus.ACTIVE);
        credential.setRotatedFromId(original.getCredentialId());
        credential.setVersion(1);
        credential.setCreateTime(now);
        credential.setScopeCodes(original.getScopeCodes());
        return credential;
    }
}
