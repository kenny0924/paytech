package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.CreateDataSourceCommand;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.integration.DataSourcePasswordVault;
import com.paytech.platform.support.dataquery.service.processor.request.CreateDataSourceRequest;

import lombok.AllArgsConstructor;

/**
 * 新建 INACTIVE 数据源，版本为 1
 * <li>按编码持资源锁；先在事务外确认 DATA_SOURCE_MANAGE、分配 ID 并把密码保存到 Vault，再在一个本地事务内复核权限、插入并登记审计。</li>
 * <li>编码已存在（含已删除）返回 400；写事务失败时 Vault 中会留下未被引用的密码记录，它不会被任何数据源读取。</li>
 * <li>新建不建立连接池：启用时才检查连接。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateDataSourceProcessor extends AbstractLockProcessor<CreateDataSourceRequest, DataSourceChange> {

    private final OperationsAuditService audit;
    private final DataSourceService dataSources;
    private final DataSourcePasswordVault vault;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(CreateDataSourceRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.sourceCodeLockKey(request.getDataSourceCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected DataSourceChange processWithinLock(CreateDataSourceRequest request) {
        // 事务外准备
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION);
        support.requirePoolSize(request.getMaxPoolSize());
        long dataSourceId = support.nextId();
        String passwordToken = vault.protect(dataSourceId, request.getPassword());

        // 本地保存与审计
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                DataSourceDetail created = dataSources.createDataSource(new CreateDataSourceCommand(dataSourceId,
                    request.getDataSourceCode(), request.getDataSourceName(), request.getConnection(), passwordToken,
                    request.getMaxPoolSize(), request.getDescription(), context.account().id(), now));
                DataSourceChange change = new DataSourceChange(null, created);
                var fact = DataQueryAuditFacts.dataSource(request.getActor(), context.member().tenantId(), change,
                    "新建数据源", now);
                audit.record(() -> fact);
                return change;
            });
    }
}
