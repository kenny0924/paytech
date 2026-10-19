package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailResult;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;

import lombok.AllArgsConstructor;

/**
 * 读取 Gateway Route 详情、Header、Query 与 Guard。
 * <li>纯查询，不加锁；会话、权限与目录投影在同一一致性读快照内完成。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetGatewayRouteProcessor extends AbstractProcessor<RouteDetailQuery, RouteDetailResult> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    @Override
    protected RouteDetailResult processRequest(RouteDetailQuery request) {
        return transactions.query(() -> management.route(request));
    }
}
