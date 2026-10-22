package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationApiDetailView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** E12 原子 API 详情：主表、字段与流程引用在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
public class GetIntegrationApiProcessor extends AbstractProcessor<GetIntegrationChildRequest, IntegrationApiDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelApiRepository channelApis;

    @Override
    protected IntegrationApiDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long channelId = children.channel(request.getIntegrationCode()).getChannelId();
            ChannelApi row = support.requireFound(channelApis.byCode(channelId, request.getCode()));
            var fields = children.savedFields(row);
            return new IntegrationApiDetailView(row.getApiCode(), row.getApiName(), row.getDescription(),
                fields.get(ApiFieldDirection.INTEGRATION_API_REQUEST),
                fields.get(ApiFieldDirection.INTEGRATION_API_RESPONSE), codec.tree(row.getDefinitionJson()),
                children.apiReferences(channelId, row.getApiCode()), row.getVersion(), row.getCreateTime(),
                row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
