package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.ChangeDataSourceStatusCommand;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeDataSourceStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 软删除数据源
 * <li>只能删除 INACTIVE 且没有 ACTIVE 模板引用的数据源；引用检查在取得行锁后读取，模板写入持有数据源共享锁，二者不会交错。</li>
 * <li>按数据源持资源锁，在一个本地事务内复核权限、条件更新并登记审计；编码不能复用，行保留供模板历史版本显示。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteDataSourceProcessor extends AbstractLockProcessor<ChangeDataSourceStatusRequest, DataSourceChange> {

    private final OperationsAuditService audit;
    private final DataSourcePoolRegistry pools;
    private final DataSourceService dataSources;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(ChangeDataSourceStatusRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.sourceLockKey(request.getDataSourceId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected DataSourceChange processWithinLock(ChangeDataSourceStatusRequest request) {
        DataSourceChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                DataSourceChange saved = dataSources.changeDataSourceStatus(new ChangeDataSourceStatusCommand(
                    request.getDataSourceId(), request.getExpectedVersion(), DataSourceStatus.DELETED,
                    context.account().id(), now));
                var fact = DataQueryAuditFacts.dataSource(request.getActor(), context.member().tenantId(), saved,
                    "删除数据源", now);
                audit.record(() -> fact);
                return saved;
            });
        pools.refresh(request.getDataSourceId());
        return change;
    }
}
