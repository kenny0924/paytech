package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
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
 * 工作区成员自助创建 API 凭证
 * <li>锁本工作区 api-credential:tenant:{tenantId}，保护有效凭证 10 个上限；锁前只读解析工作区并确认权限。</li>
 * <li>密钥与 Argon2id 校验值在事务外生成；事务内重证会话与权限、检查上限与 Scope 目录后写入并记审计。</li>
 * <li>完整密钥只在结果中出现一次，不进入日志、审计或数据库。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class CreateClientApiCredentialProcessor
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
        var workspace = access.readTransaction().execute(status -> access.require(request.getAccountId(),
            request.getSessionId(), ClientDeveloperPermissions.API_CREDENTIAL_MANAGE));
        return LockCommand.tryLock("api-credential:tenant:" + workspace.getTenantId(),
            Math.toIntExact(lockProperties.getWaitTime().toMillis()),
            Math.toIntExact(lockProperties.getLeaseTime().toMillis()));
    }

    @Override
    protected ClientApiCredentialResult processWithinLock(ClientApiCredentialRequest request) {
        // 事务外校验输入并生成密钥
        String name = ClientApiCredentialRules.name(request.getName());
        var scopeCodes = ClientApiCredentialRules.scopes(request.getScopeCodes());
        int days = ClientApiCredentialRules.validityDays(request.getValidityDays());
        var secret = ClientApiCredentialRules.generate();

        // 本地原子写入
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientDeveloperPermissions.API_CREDENTIAL_MANAGE);
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                if (credentials.activeCount(workspace.getTenantId(), now) >= ClientApiCredentialRules.ACTIVE_LIMIT) {
                    throw new BizCheckFailException("每个工作区最多保留 " + ClientApiCredentialRules.ACTIVE_LIMIT
                        + " 个有效凭证，请先撤销不再使用的凭证");
                }
                if (!new HashSet<>(credentials.catalogCodes(scopeCodes)).containsAll(scopeCodes)) {
                    throw new BizCheckFailException("所选 Scope 不在可授予目录中，请刷新后重试");
                }
                var credential = newCredential(workspace.getTenantId(), name, scopeCodes, secret.keyPrefix(),
                    now.plus(Duration.ofDays(days)), now);
                var change = new ClientApiCredentialChange(workspace, credential, secret.verifierPhc(),
                    scopeCodes.stream().map(code -> ids.nextId()).toList(), null, 0, now);
                service.create(change);
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("API_CREDENTIAL", Long.toString(credential.getCredentialId())), "CLIENT_SELF_SERVICE", now));
                return new ClientApiCredentialResult(credential, secret.credential(), now);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 新 ACTIVE 凭证对象，插入前准备 ID 与初始值。 */
    ClientApiCredential newCredential(long tenantId, String name, java.util.List<String> scopeCodes, String keyPrefix,
        java.time.Instant expiresTime, java.time.Instant now) {
        var credential = new ClientApiCredential();
        credential.setCredentialId(ids.nextId());
        credential.setTenantId(tenantId);
        credential.setKeyPrefix(keyPrefix);
        credential.setCredentialName(name);
        credential.setExpiresTime(expiresTime);
        credential.setStoredStatus(ApiCredentialStatus.ACTIVE);
        credential.setVersion(1);
        credential.setCreateTime(now);
        credential.setScopeCodes(scopeCodes);
        return credential;
    }
}
