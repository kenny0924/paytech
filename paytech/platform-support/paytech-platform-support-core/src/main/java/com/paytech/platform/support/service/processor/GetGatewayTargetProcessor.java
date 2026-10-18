package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailResult;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;

import lombok.AllArgsConstructor;

/**
 * 读取 Gateway Target 详情及维护边界。
 * <li>纯查询，不加锁；会话、权限与目录投影在同一一致性读快照内完成。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetGatewayTargetProcessor extends AbstractProcessor<TargetDetailQuery, TargetDetailResult> {
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    @Override
    protected TargetDetailResult processRequest(TargetDetailQuery request) {
        return transactions.query(() -> management.target(request));
    }
}
