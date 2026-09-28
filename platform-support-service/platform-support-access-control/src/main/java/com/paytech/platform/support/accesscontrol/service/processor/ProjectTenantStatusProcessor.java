package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ProjectTenantStatusContext;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionAuditFacts;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantProjectionResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantProjectionService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ProjectTenantStatusRequest;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 顺序投影 Client 生命周期，首次 ACTIVE 原子切换 OWNER 角色。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "tenant.projection.enabled", havingValue = "true")
public class ProjectTenantStatusProcessor
    extends AbstractLockProcessor<ProjectTenantStatusRequest, TenantProjectionResult> {
    private final Clock clock;
    private final RedisResourceLockProperties lockProperties;
    private final IdGenerator ids;
    private final OperationsAuditService audit;
    private final TenantProjectionService service;
    private final TenantProjectionSupport support;
    private final TenantProjectionTransactions transactions;
    private final TenantProjectionRoleRepository roles;
    private final TenantProjectionTenantRepository tenants;

    /** 完整提交来源与申请账号资源，保持原锁键及服务级等待/租约配置。 */
    @Override
    protected LockCommand lockCommand(ProjectTenantStatusRequest request) {
        return LockCommand.tryLocks(
            List.of("tenant:source:client:" + request.getOwnerRefId(),
                "lock:customer:account:" + request.getRequestedByAccountId()),
            Duration.ofNanos(lockProperties.requiredWaitNanos()),
            Duration.ofNanos(lockProperties.requiredLeaseNanos()));
    }

    /** 全部资源取得后开启本地事务；事务完成后由父类逆序释放。 */
    @Override
    protected TenantProjectionResult processWithinLock(ProjectTenantStatusRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** Account → Tenant → Member/Role；先比较原来源，再检查连续推进及角色切换。 */
    TenantProjectionResult execute(ProjectTenantStatusRequest request) {
        support.lockApplicant(request.getRequestedByAccountId());
        var tenant = tenants.lock(request.getOwnerRefId());
        if (tenant == null)
            throw new ServiceUnavailableException(TenantProjectionErrorCode.TENANT_PROJECTION_NOT_READY);
        var member = support.owner(tenant, request.getRequestedByAccountId());
        tenant.requireSource();
        if (request.getSourceVersion() < tenant.sourceVersion()) {
            audit.suppressReplay();
            return support.result(tenant, member);
        }
        if (request.getSourceVersion() == tenant.sourceVersion()) {
            tenant.requireSameFact(request.getPayloadHash(), request.getOwnerStatus());
            audit.suppressReplay();
            return support.result(tenant, member);
        }
        tenant.requireNextFact(request.getSourceVersion(), request.getOwnerStatus());

        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TenantProjectionRole ownerRole = null;
        TenantProjectionGrant ownerGrant = null;
        if (request.getOwnerStatus() == OperationsTenantOwnerStatus.ACTIVE
            && roles.ownerRole(tenant.tenantId()) == null) {
            ownerRole = new TenantProjectionRole(ids.nextId(), tenant.tenantId(), "TENANT_OWNER");
            ownerGrant =
                new TenantProjectionGrant(ids.nextId(), tenant.tenantId(), member.memberId(), ownerRole.roleId());
        }
        var context = new ProjectTenantStatusContext(tenant, member, request.getSourceVersion(),
            request.getPayloadHash(), request.getOwnerStatus(), ownerRole, ownerGrant, now);
        service.projectStatus(context);
        var result = support.result(context.getTenant(), context.getMember());
        audit.record(() -> TenantProjectionAuditFacts.projected(result, false, context.activatesOwner(), now));
        return result;
    }
}
