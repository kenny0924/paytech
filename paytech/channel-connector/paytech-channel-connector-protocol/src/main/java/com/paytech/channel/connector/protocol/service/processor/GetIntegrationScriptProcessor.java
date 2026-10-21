package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationScript;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationScriptDetailView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationScriptRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** E18 公共脚本详情：脚本与引用它的原子 API 在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
public class GetIntegrationScriptProcessor
    extends AbstractProcessor<GetIntegrationChildRequest, IntegrationScriptDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationScriptRepository integrationScripts;

    @Override
    protected IntegrationScriptDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            IntegrationScript row = support.requireFound(integrationScripts.byCode(integrationId, request.getCode()));
            var referencedBy = children.scriptUsers(integrationId, row.getScriptCode()).stream()
                .map(IntegrationApi::getApiCode).sorted().toList();
            return new IntegrationScriptDetailView(row.getScriptCode(), row.getScriptName(), row.getExecutionStage(),
                codec.parameters(row.getParameterDefinition()), row.getSourceCode(), row.getDescription(),
                referencedBy, row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
