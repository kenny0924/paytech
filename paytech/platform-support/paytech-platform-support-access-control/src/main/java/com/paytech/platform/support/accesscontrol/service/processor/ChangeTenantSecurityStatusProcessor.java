package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.TenantAccessManagementProperties;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ChangeTenantSecurityStatusContext;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantSecurityStatusResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantAccessManagementService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ChangeTenantSecurityStatusRequest;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationChanged;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationField;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.ConfigurationFieldChange;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 暂停或恢复一个租户的安全状态，不修改来源业务及成员授权事实。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeTenantSecurityStatusProcessor
    extends AbstractLockProcessor<ChangeTenantSecurityStatusRequest, TenantSecurityStatusResult> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final TenantAccessRepository tenants;
    private final TenantAccessManagementService service;
    private final TenantAccessManagementSupport support;
    private final OperationsManagementTransactions transactions;
    private final TenantAccessManagementProperties properties;
    private final OperationsManagementPolicyService policy;

    /** 暂停与恢复共同锁定实际目标 Tenant，获取锁发生在数据库事务之前。 */
    @Override
    protected LockCommand lockCommand(ChangeTenantSecurityStatusRequest request) {
        return LockCommand.tryLock("access-tenant:" + request.getTenantId(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 共用本地事务及异常转换；事务完成后由模板释放资源锁。 */
    @Override
    protected TenantSecurityStatusResult processWithinLock(ChangeTenantSecurityStatusRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 当前授权和目标版本确认后，一次构造 Context 并保存原对象。 */
    TenantSecurityStatusResult execute(ChangeTenantSecurityStatusRequest request) {
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "TENANT_ACCESS_MANAGE");
        var tenant = support.requireTenant(tenants.lock(request.getTenantId()));
        tenant.requireVersion(request.getExpectedVersion());
        if (tenant.getSecurityStatus() == request.getSecurityStatus()) {
            audit.suppressReplay();
            return tenant.securityStatusResult();
        }
        tenant.requireVersionAdvance();
        var context = new ChangeTenantSecurityStatusContext(tenant, request.getSecurityStatus(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        // 原字段必须在 Repository 同步对象之前冻结，审计仍在业务提交后尽力写入。
        var fact = new ConfigurationChanged(new Identity(request.getActor(), actor.member().tenantId()),
            new Target("ACCESS_TENANT", tenant.getTenantId().toString()), tenant.getVersion(),
            request.getExpectedVersion() + 1,
            List.of(new ConfigurationFieldChange(ConfigurationField.SECURITY_STATUS,
                tenant.getSecurityStatus().getCode(), context.getSecurityStatus().getCode())),
            request.getReason(), context.getUpdateTime());
        service.changeSecurityStatus(context);
        audit.record(() -> fact);
        return tenant.securityStatusResult();
    }
}
