package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceCapabilities;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourcePolicy;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ListConsoleResourcesRequest;

import lombok.AllArgsConstructor;

/** 授权后返回当前资源查询；沿用普通读取，不建立多余事务或资源锁。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListConsoleResourcesProcessor
    extends AbstractProcessor<ListConsoleResourcesRequest, List<ConsoleResourceView>> {
    private final OperationsManagementPolicyService policy;
    private final ConfigurationCatalogRepository catalog;
    private final ConsoleResourcePolicy resourcePolicy;

    /** 保留当前会话及独立读取权限，协议投影交给 Converter。 */
    @Override
    protected List<ConsoleResourceView> processRequest(ListConsoleResourcesRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), "CONSOLE_RESOURCE_READ");
        var tree = catalog.resources(ConsoleResourceCapabilities.MAX_RESOURCES + 1);
        resourcePolicy.requireTreeSize(tree.size());
        tree.forEach(row -> row.prepare(resourcePolicy, tree));
        return List.copyOf(tree);
    }
}
