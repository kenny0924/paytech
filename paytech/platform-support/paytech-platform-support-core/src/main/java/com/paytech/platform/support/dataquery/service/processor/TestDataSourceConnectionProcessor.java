package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.engine.QueryEngines;
import com.paytech.platform.support.dataquery.integration.DataSourcePasswordVault;
import com.paytech.platform.support.dataquery.service.processor.request.TestDataSourceConnectionRequest;
import com.paytech.platform.support.dataquery.service.processor.response.ConnectionTestResult;

import lombok.AllArgsConstructor;

/**
 * 测试草稿连接配置，不保存配置
 * <li>事务外确认 DATA_SOURCE_MANAGE，再以单个连接执行 SELECT 1 并检查账号权限；带 dataSourceId 且未传密码时读取已保存的密码。</li>
 * <li>带 dataSourceId 时在短事务内回写最后一次检查（不改变版本）；检查结果无论通过与否都正常返回。</li>
 * <li>只回写说明性的检查字段，不持资源锁。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TestDataSourceConnectionProcessor
    extends AbstractProcessor<TestDataSourceConnectionRequest, ConnectionTestResult> {

    private final QueryEngines engines;
    private final OperationsAuditService audit;
    private final DataSourceService dataSources;
    private final DataSourcePasswordVault vault;
    private final DataQueryManagementSupport support;

    @Override
    protected ConnectionTestResult processRequest(TestDataSourceConnectionRequest request) {
        // 事务外检查
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION);
        DataSourceDetail saved = request.getDataSourceId() == null ? null : requireTestable(request.getDataSourceId());
        String password = password(request, saved);
        ConnectionCheckResult check = engines.forType(request.getConnection().getEngineType())
            .check(request.getConnection(), password);
        var checkedAt = support.now();
        if (saved == null) {
            return new ConnectionTestResult(check, checkedAt);
        }

        // 回写最后一次检查
        support.write(request.getActor(), request.getSession(), DataQueryManagementSupport.SOURCE_MANAGE_PERMISSION,
            context -> {
                dataSources.recordConnectionCheck(saved.getDataSourceId(), check, checkedAt);
                var fact = DataQueryAuditFacts.connectionTested(request.getActor(), context.member().tenantId(),
                    saved.getDataSourceId(), check.result().getCode(), checkedAt);
                audit.record(() -> fact);
                return null;
            });
        return new ConnectionTestResult(check, checkedAt);
    }

    /** 已保存的数据源必须存在且未删除。 */
    DataSourceDetail requireTestable(long dataSourceId) {
        DataSourceDetail saved = DataQueryManagementSupport.requireFound(dataSources.findDataSource(dataSourceId));
        if (saved.statusIs(DataSourceStatus.DELETED)) {
            throw new BizCheckFailException("数据源已删除: " + saved.getDataSourceCode());
        }
        return saved;
    }

    /** 优先使用请求中的密码；草稿没有已保存数据源时必须提供。 */
    String password(TestDataSourceConnectionRequest request, DataSourceDetail saved) {
        if (request.getPassword() != null) {
            return request.getPassword();
        }
        if (saved == null) {
            throw new InvalidParameterException("未指定数据源时必须提供密码");
        }
        return vault.reveal(saved.getPasswordToken());
    }
}
