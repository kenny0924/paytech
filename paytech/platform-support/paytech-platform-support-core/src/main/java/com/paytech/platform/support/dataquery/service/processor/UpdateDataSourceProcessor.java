package com.paytech.platform.support.dataquery.service.processor;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.UpdateDataSourceCommand;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.engine.QueryEngines;
import com.paytech.platform.support.dataquery.integration.DataSourcePasswordVault;
import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateDataSourceRequest;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改数据源，版本加一
 * <li>按数据源持资源锁；事务外确认权限、保存新密码到 Vault；ACTIVE 数据源的连接参数或密码有变化时先以新配置做连接检查，
 * 未通过返回 400 且不保存，原配置继续生效。</li>
 * <li>本地事务内复核权限、条件更新（通过的检查结果一并回写）并登记审计；完全无变化时不递增版本、不登记审计。</li>
 * <li>提交后立即刷新本地连接池，其他实例在一个刷新周期内切换。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateDataSourceProcessor extends AbstractLockProcessor<UpdateDataSourceRequest, DataSourceChange> {

    private final QueryEngines engines;
    private final OperationsAuditService audit;
    private final DataSourcePoolRegistry pools;
    private final DataSourceService dataSources;
    private final DataSourcePasswordVault vault;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(UpdateDataSourceRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.sourceLockKey(request.getDataSourceId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected DataSourceChange processWithinLock(UpdateDataSourceRequest request) {
        // 事务外准备
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION);
        support.requirePoolSize(request.getMaxPoolSize());
        DataSourceDetail current = DataQueryManagementSupport.requireFound(
            dataSources.findDataSource(request.getDataSourceId()));
        String passwordToken = request.getPassword() == null ? null
            : vault.protect(request.getDataSourceId(), request.getPassword());
        ConnectionCheckResult check = checkActiveChange(current, request, passwordToken);

        // 本地保存与审计
        DataSourceChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                DataSourceChange saved = dataSources.updateDataSource(new UpdateDataSourceCommand(
                    request.getDataSourceId(), request.getExpectedVersion(), request.getDataSourceName(),
                    request.getConnection(), passwordToken, request.getMaxPoolSize(), request.getDescription(),
                    context.account().id(), now));
                if (!saved.changed()) {
                    // 无变化保存不追加审计，也不递增版本。
                    audit.suppressReplay();
                    return saved;
                }
                if (check != null) {
                    dataSources.recordConnectionCheck(request.getDataSourceId(), check, now);
                }
                var fact = DataQueryAuditFacts.dataSource(request.getActor(), context.member().tenantId(), saved,
                    "修改数据源", now);
                audit.record(() -> fact);
                return saved;
            });

        // 提交后刷新本地连接池
        if (change.changed()) {
            pools.refresh(request.getDataSourceId());
        }
        return change;
    }

    /**
     * ACTIVE 数据源的连接参数或密码变化时，以新配置做连接检查；未通过直接拒绝。
     *
     * @param current 当前数据源
     * @param request 请求
     * @param passwordToken 新密码 Token；保留原密码时为 null
     * @return 通过的检查结果；无需检查时为 null
     */
    ConnectionCheckResult checkActiveChange(DataSourceDetail current, UpdateDataSourceRequest request,
        String passwordToken) {
        if (!current.statusIs(DataSourceStatus.ACTIVE)
            || passwordToken == null && sameConnection(current.getConnection(), request.getConnection())) {
            return null;
        }
        String password = request.getPassword() != null ? request.getPassword()
            : vault.reveal(current.getPasswordToken());
        ConnectionCheckResult check = engines.forType(request.getConnection().getEngineType())
            .check(request.getConnection(), password);
        if (!check.passed()) {
            throw new BizCheckFailException("连接检查未通过，原配置继续生效: " + check.message());
        }
        return check;
    }

    /** 连接参数是否相同。 */
    static boolean sameConnection(DataSourceConnectionConfig current, DataSourceConnectionConfig next) {
        return current.getEngineType() == next.getEngineType() && Objects.equals(current.getHost(), next.getHost())
            && current.getPort() == next.getPort() && Objects.equals(current.getDatabaseName(), next.getDatabaseName())
            && Objects.equals(current.getUsername(), next.getUsername())
            && current.getConnectionParams().equals(next.getConnectionParams());
    }
}
