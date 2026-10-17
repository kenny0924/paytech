package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProvisionTenantContext;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionAuditFacts;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantProjectionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ProvisionTenantRequest;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** Client 第一版来源开通；公共模板统一基础校验和双资源锁。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class ProvisionTenantProcessor extends AbstractLockProcessor<ProvisionTenantRequest, TenantProjectionResult> {
    private final Clock clock;
    private final RedisResourceLockProperties lockProperties;
    private final IdGenerator ids;
    private final OperationsAuditService audit;
    private final TenantProjectionService service;
    private final TenantProjectionSupport support;
    private final TenantProjectionTransactions transactions;
    private final TenantProjectionTenantRepository tenants;

    /** 第一版来源约束属于用例校验；字段约束由父类先执行。 */
    @Override
    protected void validate(ProvisionTenantRequest request) {
        if (request.getSourceVersion() != 1)
            throw new InvalidParameterException("开通命令必须使用来源第一版");
    }

    /** 完整提交来源与申请账号资源，保持原锁键及服务级等待/租约配置。 */
    @Override
    protected LockCommand lockCommand(ProvisionTenantRequest request) {
        return LockCommand.tryLocks(
            List.of("tenant:source:client:" + request.getOwnerRefId(),
                "lock:customer:account:" + request.getRequestedByAccountId()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 全部资源取得后开启本地事务；事务完成后由父类逆序释放。 */
    @Override
    protected TenantProjectionResult processWithinLock(ProvisionTenantRequest request) {
        return transactions.provision(() -> execute(request));
    }

    /** Account → Tenant → Member/Role；原事实先于首次创建或状态推进资格检查。 */
    TenantProjectionResult execute(ProvisionTenantRequest request) {
        var account = support.lockApplicant(request.getRequestedByAccountId());
        var tenant = tenants.lock(request.getOwnerRefId());
        if (tenant != null) {
            var member = support.owner(tenant, request.getRequestedByAccountId());
            tenant.requireSource();
            if (tenant.sourceVersion() > request.getSourceVersion())
                throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_STALE);
            if (tenant.sourceVersion() != request.getSourceVersion())
                throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
            tenant.requireSameFact(request.getPayloadHash(), OperationsTenantOwnerStatus.PENDING);
            audit.suppressReplay();
            return support.result(tenant, member);
        }
        if (account.status() != OperationsAccountStatus.ACTIVE)
            throw new FailException(TenantProjectionErrorCode.TENANT_APPLICANT_INVALID);

        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        tenant = new TenantProjection(ids.nextId(), request.getOwnerRefId(), OperationsTenantOwnerStatus.PENDING, 1,
            request.getPayloadHash(), 1);
        var member = new TenantProjectionMember(ids.nextId(), request.getRequestedByAccountId());
        var role = new TenantProjectionRole(ids.nextId(), tenant.tenantId(), "TENANT_ONBOARDING_OWNER");
        var grant = new TenantProjectionGrant(ids.nextId(), tenant.tenantId(), member.memberId(), role.roleId());
        var context = new ProvisionTenantContext(tenant, member, role, grant, now);
        service.provision(context);
        var result = support.result(context.getTenant(), context.getMember());
        audit.record(() -> TenantProjectionAuditFacts.projected(result, true, false, now));
        return result;
    }
}
