package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationScriptDetailView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
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
    private final ChannelScriptRepository channelScripts;

    @Override
    protected IntegrationScriptDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long channelId = children.channel(request.getIntegrationCode()).getChannelId();
            ChannelScript row = support.requireFound(channelScripts.byCode(channelId, request.getCode()));
            var referencedBy = children.scriptUsers(channelId, row.getScriptCode()).stream()
                .map(ChannelApi::getApiCode).sorted().toList();
            return new IntegrationScriptDetailView(row.getScriptCode(), row.getScriptName(), row.getExecutionStage(),
                codec.parameters(row.getParameterDefinition()), row.getSourceCode(), row.getDescription(),
                referencedBy, row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
