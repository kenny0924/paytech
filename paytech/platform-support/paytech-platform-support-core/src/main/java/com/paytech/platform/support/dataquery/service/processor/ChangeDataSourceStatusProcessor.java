package com.paytech.platform.support.dataquery.service.processor;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.ChangeDataSourceStatusCommand;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.engine.QueryEngines;
import com.paytech.platform.support.dataquery.integration.DataSourcePasswordVault;
import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeDataSourceStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用数据源
 * <li>启用：事务外读取 Vault 密码并以已保存配置做连接检查；检查结果总是回写，未通过时提交回写后返回 400，状态不变。</li>
 * <li>停用：新查询立即被拒绝；本实例提交后退役连接池，进行中的查询正常结束后关闭，其他实例一个刷新周期内退役。</li>
 * <li>按数据源持资源锁；目标状态与当前相同时不递增版本、不登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeDataSourceStatusProcessor
    extends AbstractLockProcessor<ChangeDataSourceStatusRequest, DataSourceChange> {

    private final QueryEngines engines;
    private final OperationsAuditService audit;
    private final DataSourcePoolRegistry pools;
    private final DataSourceService dataSources;
    private final DataSourcePasswordVault vault;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(ChangeDataSourceStatusRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.sourceLockKey(request.getDataSourceId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected DataSourceChange processWithinLock(ChangeDataSourceStatusRequest request) {
        // 事务外准备
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION);
        DataSourceDetail current = DataQueryManagementSupport.requireFound(
            dataSources.findDataSource(request.getDataSourceId()));
        ConnectionCheckResult check = activationCheck(current, request);

        // 本地保存与审计
        DataSourceChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                if (check != null) {
                    dataSources.recordConnectionCheck(request.getDataSourceId(), check, now);
                }
                if (check != null && !check.passed()) {
                    audit.suppressReplay();
                    return null;
                }
                DataSourceChange saved = dataSources.changeDataSourceStatus(new ChangeDataSourceStatusCommand(
                    request.getDataSourceId(), request.getExpectedVersion(), request.getTargetStatus(),
                    context.account().id(), now));
                record(request, context.member().tenantId(), saved, now);
                return saved;
            });
        if (change == null) {
            throw new BizCheckFailException("连接检查未通过，不能启用: " + check.message());
        }

        // 提交后刷新本地连接池
        if (change.changed()) {
            pools.refresh(request.getDataSourceId());
        }
        return change;
    }

    /** INACTIVE → ACTIVE 时以已保存配置做连接检查；其他转换不检查。 */
    ConnectionCheckResult activationCheck(DataSourceDetail current, ChangeDataSourceStatusRequest request) {
        if (request.getTargetStatus() != DataSourceStatus.ACTIVE || !current.statusIs(DataSourceStatus.INACTIVE)) {
            return null;
        }
        return engines.forType(current.getConnection().getEngineType()).check(current.getConnection(),
            vault.reveal(current.getPasswordToken()));
    }

    /** 有实际变化时登记审计，否则抑制。 */
    void record(ChangeDataSourceStatusRequest request, long tenantId, DataSourceChange saved, Instant now) {
        if (!saved.changed()) {
            audit.suppressReplay();
            return;
        }
        var fact = DataQueryAuditFacts.dataSource(request.getActor(), tenantId, saved,
            request.getTargetStatus() == DataSourceStatus.ACTIVE ? "启用数据源" : "停用数据源", now);
        audit.record(() -> fact);
    }
}
