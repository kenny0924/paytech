package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.ImplementationDetailView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** E24 渠道标准 API 实现详情：接入方、标准 API 与实现行在同一只读快照中读取。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetImplementationProcessor extends AbstractProcessor<GetIntegrationChildRequest, ImplementationDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final OperationsManagementPolicyService policy;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected ImplementationDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            var standardApi = flows.standardApi(request.getCode());
            IntegrationImplementation row = support.requireFound(implementations.byKey(integrationId,
                standardApi.getStandardApiId()));
            return new ImplementationDetailView(standardApi.getStandardApiCode(),
                codec.tree(row.getFlowDefinitionJson()), row.getVersion(), row.getCreateTime(), row.getUpdateTime(),
                row.getUpdatedBy());
        });
    }
}
