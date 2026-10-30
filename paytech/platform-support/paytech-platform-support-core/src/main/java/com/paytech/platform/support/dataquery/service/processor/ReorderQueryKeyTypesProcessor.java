package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeReorder;
import com.paytech.platform.support.dataquery.domain.ReorderQueryKeyTypesCommand;
import com.paytech.platform.support.dataquery.domainservice.service.QueryKeyTypeService;
import com.paytech.platform.support.dataquery.service.processor.request.ReorderQueryKeyTypesRequest;

import lombok.AllArgsConstructor;

/**
 * 保存全部键类型的新顺序（页面拖动排序）
 * <li>持 lock:data-query:key-type-order；一个本地事务内复核权限、对全部键类型加排他锁，items 必须恰好覆盖全部键类型
 * 且不重复（400），任一版本不符返回 409 并整体回滚；按位置重写 sort_no = 序号 × 10，每项版本加一，登记一条审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReorderQueryKeyTypesProcessor
    extends AbstractLockProcessor<ReorderQueryKeyTypesRequest, QueryKeyTypeReorder> {

    private final QueryKeyTypeService keyTypes;
    private final OperationsAuditService audit;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(ReorderQueryKeyTypesRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.keyTypeOrderLockKey(), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected QueryKeyTypeReorder processWithinLock(ReorderQueryKeyTypesRequest request) {
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryKeyTypeReorder reorder = keyTypes.reorderQueryKeyTypes(new ReorderQueryKeyTypesCommand(
                    request.getItems(), context.account().id(), now));
                var fact = DataQueryAuditFacts.keyTypeOrder(request.getActor(), context.member().tenantId(), reorder,
                    now);
                audit.record(() -> fact);
                return reorder;
            });
    }
}
