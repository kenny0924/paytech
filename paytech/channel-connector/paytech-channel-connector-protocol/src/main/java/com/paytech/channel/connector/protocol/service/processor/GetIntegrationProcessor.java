package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationApiEndpoint;
import com.paytech.channel.connector.protocol.domain.EndpointUsage;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationDetailView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationScriptRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/** E07 接入方详情：地址、地址引用与下级数量在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
public class GetIntegrationProcessor extends AbstractProcessor<GetExternalApiConfigRequest, IntegrationDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationScriptRepository integrationScripts;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected IntegrationDetailView processRequest(GetExternalApiConfigRequest request) {
        return transactions.query(() -> {
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
