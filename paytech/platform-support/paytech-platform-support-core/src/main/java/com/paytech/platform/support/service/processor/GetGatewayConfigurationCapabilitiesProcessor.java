package com.paytech.platform.support.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCapabilities.Capabilities;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.service.processor.request.GatewayConfigurationCapabilitiesRequest;

import lombok.AllArgsConstructor;

/**
 * 读取 Gateway 配置编译器能力：通道、安全档案、上限与禁止 Header。
 * <li>纯查询，不加锁、不开事务；能力来自同一 Platform 编译器，按查询权限保护。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetGatewayConfigurationCapabilitiesProcessor
    extends AbstractProcessor<GatewayConfigurationCapabilitiesRequest, Capabilities> {
    private final GatewayConfigurationManagementService management;

    @Override
    protected Capabilities processRequest(GatewayConfigurationCapabilitiesRequest request) {
        return management.capabilities(request.getActorId(), request.getSessionId());
    }
}
