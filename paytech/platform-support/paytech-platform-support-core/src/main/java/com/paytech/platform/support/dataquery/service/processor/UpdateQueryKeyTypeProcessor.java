package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domain.UpdateQueryKeyTypeCommand;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateQueryKeyTypeRequest;

import lombok.AllArgsConstructor;

/**
 * 修改键类型；编码不可修改
 * <li>按键类型持资源锁；事务前确认权限、键类型存在并检查入口正则子集（422 field valuePattern）。</li>
 * <li>本地事务内复核权限、排他锁读取并核对原版本（409）；被 ACTIVE 模板当前版本引用时修改值类型返回 400，
 * 分组、默认角色、名称与顺序可改；登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateQueryKeyTypeProcessor extends AbstractLockProcessor<UpdateQueryKeyTypeRequest, QueryKeyTypeChange> {

    private final QueryKeyTypeService keyTypes;
    private final OperationsAuditService audit;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(UpdateQueryKeyTypeRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.keyTypeLockKey(request.getKeyTypeId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryKeyTypeChange processWithinLock(UpdateQueryKeyTypeRequest request) {
        // 权限、存在与正则
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        DataQueryManagementSupport.requireFound(keyTypes.findQueryKeyType(request.getKeyTypeId()));
        DataQueryManagementSupport.requireValidPattern(request.getDefinition().getValuePattern());

        // 本地保存与审计
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryKeyTypeChange saved = keyTypes.updateQueryKeyType(new UpdateQueryKeyTypeCommand(
                    request.getKeyTypeId(), request.getExpectedVersion(), request.getDefinition(),
                    context.account().id(), now));
                var fact = DataQueryAuditFacts.keyType(request.getActor(), context.member().tenantId(), saved,
                    "修改键类型", now);
                audit.record(() -> fact);
                return saved;
            });
    }
}
