package com.paytech.platform.support.accesscontrol.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.configuration.PermissionManagementProperties;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.PermissionAuditFacts;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.UpdatePermissionContext;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.PermissionManagementService;
import com.paytech.platform.support.accesscontrol.service.processor.request.UpdatePermissionRequest;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/** 只更新风险等级，无变化不递增版本或追加审计。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdatePermissionProcessor extends AbstractLockProcessor<UpdatePermissionRequest, ConfigurationMutation> {
    private final Clock clock;
    private final OperationsAuditService audit;
    private final PermissionManagementService service;
    private final PermissionManagementSupport support;
    private final PermissionManagementProperties properties;
    private final PermissionManagementTransactions transactions;
    private final OperationsManagementPolicyService policy;

    /** 创建、编辑和启停按同一不可变权限编码协调，数据库保护继续保留。 */
    @Override
    protected LockCommand lockCommand(UpdatePermissionRequest request) {
        return LockCommand.tryLock("permission:" + request.getPermissionCode(),
            Math.toIntExact(properties.getLockWaitTime().toMillis()),
            Math.toIntExact(properties.getLockLeaseTime().toMillis()));
    }

    /** 锁内开启一次本地事务，不访问外部能力。 */
    @Override
    protected ConfigurationMutation processWithinLock(UpdatePermissionRequest request) {
        return transactions.write(() -> execute(request));
    }

    /** 当前授权、原事实与版本校验通过后构造 Context，保存成功才登记审计。 */
    ConfigurationMutation execute(UpdatePermissionRequest request) {
        var actor = policy.authorizeManagement(request.getActor(), request.getSession(), null, List.of(), null, null,
            false, "PERMISSION_MANAGE");
        var permission = support.lock(request.getPermissionCode(), request.getExpectedVersion());
        var context = new UpdatePermissionContext(permission, request.getRiskLevel(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (context.unchanged()) {
            // 原有无变化保存不追加审计；不涉及请求幂等或回执重放。
            audit.suppressReplay();
            return permission.mutation();
        }
        var fact = PermissionAuditFacts.changed(request.getActor(), actor.member().tenantId(), permission,
            context.getRiskLevel(), permission.status(), request.getReason(), context.getUpdateTime());
        service.update(context);
        audit.record(() -> fact);
        return permission.mutation();
    }
}
