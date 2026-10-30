package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.CreateQueryKeyTypeCommand;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.service.processor.request.CreateQueryKeyTypeRequest;

import lombok.AllArgsConstructor;

/**
 * 新建键类型，状态 ACTIVE
 * <li>按编码持资源锁；事务前确认 DATA_QUERY_TEMPLATE_MANAGE 并检查入口正则子集，不合法返回 422（field valuePattern）。</li>
 * <li>本地事务内复核权限、插入键类型并登记审计；编码已存在返回 400。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateQueryKeyTypeProcessor extends AbstractLockProcessor<CreateQueryKeyTypeRequest, QueryKeyTypeChange> {

    private final QueryKeyTypeService keyTypes;
    private final OperationsAuditService audit;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(CreateQueryKeyTypeRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.keyTypeCodeLockKey(request.getKeyTypeCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryKeyTypeChange processWithinLock(CreateQueryKeyTypeRequest request) {
        // 权限与正则
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        DataQueryManagementSupport.requireValidPattern(request.getDefinition().getValuePattern());

        // 本地保存与审计
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryKeyTypeChange saved = keyTypes.createQueryKeyType(new CreateQueryKeyTypeCommand(support.nextId(),
                    request.getKeyTypeCode(), request.getDefinition(), context.account().id(), now));
                var fact = DataQueryAuditFacts.keyType(request.getActor(), context.member().tenantId(), saved,
                    "新建键类型", now);
                audit.record(() -> fact);
                return saved;
            });
    }
}
