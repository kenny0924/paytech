package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryKeyTypeStatusCommand;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeQueryKeyTypeStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用键类型
 * <li>按键类型持资源锁，在一个本地事务内复核权限、排他锁读取并核对原版本、条件更新并登记审计。</li>
 * <li>停用时以共享锁读取 ACTIVE 模板当前版本，被引用则返回 400 并列出前 5 个模板编码；与模板保存、启用时对键类型的共享锁互斥。</li>
 * <li>目标状态与当前相同时不递增版本、不登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeQueryKeyTypeStatusProcessor
    extends AbstractLockProcessor<ChangeQueryKeyTypeStatusRequest, QueryKeyTypeChange> {

    private final QueryKeyTypeService keyTypes;
    private final OperationsAuditService audit;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(ChangeQueryKeyTypeStatusRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.keyTypeLockKey(request.getKeyTypeId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryKeyTypeChange processWithinLock(ChangeQueryKeyTypeStatusRequest request) {
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryKeyTypeChange saved = keyTypes.changeQueryKeyTypeStatus(new ChangeQueryKeyTypeStatusCommand(
                    request.getKeyTypeId(), request.getExpectedVersion(), request.getTargetStatus(),
                    context.account().id(), now));
                if (!saved.changed()) {
                    audit.suppressReplay();
                    return saved;
                }
                var fact = DataQueryAuditFacts.keyType(request.getActor(), context.member().tenantId(), saved,
                    request.getTargetStatus() == QueryKeyTypeStatus.ACTIVE ? "启用键类型" : "停用键类型", now);
                audit.record(() -> fact);
                return saved;
            });
    }
}
