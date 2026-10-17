package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.Capabilities;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceCapabilities;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryConsoleResourceCapabilitiesRequest;

import lombok.AllArgsConstructor;

/** 授权后返回当前资源查询；沿用普通读取，不建立多余事务或资源锁。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryConsoleResourceCapabilitiesProcessor
    extends AbstractProcessor<QueryConsoleResourceCapabilitiesRequest, Capabilities> {
    private final OperationsManagementPolicyService policy;
    private final ConsoleResourceCapabilities capabilities;

    /** 保留当前会话及独立读取权限，协议投影交给 Converter。 */
    @Override
    protected Capabilities processRequest(QueryConsoleResourceCapabilitiesRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), "CONSOLE_RESOURCE_READ");
        return capabilities.current();
    }
}
