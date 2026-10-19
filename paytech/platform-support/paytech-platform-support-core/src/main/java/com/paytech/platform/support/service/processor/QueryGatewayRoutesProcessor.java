package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.Page;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RoutePageQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteSummaryResult;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;

import lombok.AllArgsConstructor;

/**
 * 分页查询 Gateway Route。
 * <li>纯查询，不加锁；会话、权限与目录投影在同一一致性读快照内完成。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryGatewayRoutesProcessor extends AbstractProcessor<RoutePageQuery, Page<RouteSummaryResult>> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    @Override
    protected Page<RouteSummaryResult> processRequest(RoutePageQuery request) {
        return transactions.query(() -> management.routes(request));
    }
}
