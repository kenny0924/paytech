package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.platform.support.integration.domain.EndpointUsage;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.IntegrationDetailView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.service.processor.request.GetExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/** E07 接入方详情：地址、地址引用与下级数量在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetIntegrationProcessor extends AbstractProcessor<GetExternalApiConfigRequest, IntegrationDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptRepository integrationScripts;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected IntegrationDetailView processRequest(GetExternalApiConfigRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            Integration row = support.requireFound(integrations.byCode(request.getCode()));
            long id = row.getIntegrationId();
            List<IntegrationApiEndpoint> references = integrationApis.endpointReferences(id);
            var endpoints = codec.endpoints(row.getEndpoints()).stream()
                .map(endpoint -> new EndpointUsage(endpoint.getEndpointCode(), endpoint.getBaseUrl(), endpoint.isSandbox(),
                    references.stream().filter(item -> endpoint.getEndpointCode().equals(item.getEndpointCode()))
                        .map(IntegrationApiEndpoint::getApiCode).toList()))
                .toList();
            return new IntegrationDetailView(row.getIntegrationCode(), row.getIntegrationName(),
                row.getDescription(), endpoints, references.size(),
                integrationScripts.countByIntegrations(List.of(id)).getOrDefault(id, 0L),
                implementations.allByIntegration(id).size(), row.getVersion(), row.getCreateTime(),
                row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
