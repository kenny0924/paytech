package com.paytech.platform.support.accesscontrol.service.processor;

import static com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementPermissions.CREDENTIAL_REVOKE;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiCredentialManagement;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementModels.Mutation;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.RevokeApiCredentialContext;
import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiCredentialManagementRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ApiCredentialManagementService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.RevokeApiCredentialRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 指定凭证撤销：按凭证加锁，复核当前授权及版本后在本库原子提交。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RevokeApiCredentialProcessor extends AbstractLockProcessor<RevokeApiCredentialRequest, Mutation> {

    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final OperationsManagementPolicyService policy;
    private final ApiCredentialManagementRepository repository;
    private final ApiCredentialManagementService credentials;
    private final RedisResourceLockProperties lockProperties;
    private final OperationsAuditService audit;

    /** 模板统一添加 lock: 前缀；沿用服务级有限等待与租约，锁覆盖完整本地事务。 */
    @Override
    protected LockCommand lockCommand(RevokeApiCredentialRequest request) {
        return LockCommand.tryLock("api-credential:" + request.getCredentialId(),
            Math.toIntExact(lockProperties.getWaitTime().toMillis()),
            Math.toIntExact(lockProperties.getLeaseTime().toMillis()));
    }

    /** 取得资源锁后开启 READ COMMITTED 短事务，提交或回滚后由模板释放锁。 */
    @Override
    protected Mutation processWithinLock(RevokeApiCredentialRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(15);
        try {
            return transaction.execute(status -> execute(request));
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 每次请求均复核当前授权，锁定目标并核对版本。 */
    Mutation execute(RevokeApiCredentialRequest request) {
        audit.observeTransaction();
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(),
            null, null, false, CREDENTIAL_REVOKE);
        ApiCredentialManagement credential = requireCredential(request);
        if (credential.getStoredStatus() == ApiCredentialStatus.REVOKED) {
            // 状态未改变时沿用审计抑制能力，不追加第二条变更审计。
            audit.suppressReplay();
            return toResult(credential);
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        RevokeApiCredentialContext context = new RevokeApiCredentialContext(credential, now);
        ConfigurationChanged fact = auditFact(request, context, actor.member().tenantId());
        credentials.revoke(context);
        audit.record(() -> fact);
        return toResult(credential);
    }

    /** 锁定原始对象，复核 CLIENT 关联与调用方期望版本，不限制已过期凭证或失效 Tenant。 */
    ApiCredentialManagement requireCredential(RevokeApiCredentialRequest request) {
        ApiCredentialManagement credential = repository.lock(request.getCredentialId());
        if (credential == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        credential.validateForManagement();
        if (credential.getVersion() != request.getExpectedVersion()
            || (credential.getStoredStatus() != ApiCredentialStatus.REVOKED && credential.getVersion() == Integer.MAX_VALUE)) {
            throw new ConcurrentRequestException("凭证已被其他操作修改，请刷新后重试");
        }
        return credential;
    }

    /** 在保存回写前冻结受控审计差异；实际审计追加仍由既有 Controller 审计链完成。 */
    ConfigurationChanged auditFact(RevokeApiCredentialRequest request, RevokeApiCredentialContext context, long actorTenantId) {
        ApiCredentialManagement credential = context.getCredential();
        return new ConfigurationChanged(new Identity(request.getActor(), actorTenantId),
            new Target("API_CREDENTIAL", Long.toString(credential.getCredentialId())), credential.getVersion(),
            credential.getVersion() + 1, List.of(new ConfigurationFieldChange(ConfigurationField.STATUS,
                credential.getStoredStatus().getCode(), ApiCredentialStatus.REVOKED.getCode())),
            request.getReason(), context.getRevokeTime());
    }

    /** 从本次已校验或保存回写的凭证构造结果。 */
    Mutation toResult(ApiCredentialManagement credential) {
        return new Mutation(credential.getCredentialId(), Long.toString(credential.getCredentialId()),
            credential.getVersion());
    }
}
