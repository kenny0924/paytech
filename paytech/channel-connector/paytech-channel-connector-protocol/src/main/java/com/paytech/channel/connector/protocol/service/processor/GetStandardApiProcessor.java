package com.paytech.channel.connector.protocol.service.processor;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ConfigurationReference;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.StandardApiDetailView;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/** E02 标准 API 详情：主表、字段与实现方在同一只读快照中读取，避免组合出从未同时存在的配置。 */
@Component
@AllArgsConstructor
public class GetStandardApiProcessor extends AbstractProcessor<GetExternalApiConfigRequest, StandardApiDetailView> {

    private final ApiFieldRepository fields;
    private final ChannelRepository channels;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final ChannelImplementationRepository implementations;

    @Override
    protected StandardApiDetailView processRequest(GetExternalApiConfigRequest request) {
        return transactions.query(() -> {
            StandardApi row = support.requireFound(standardApis.byCode(request.getCode()));
            var fieldRows = fields.byApis(List.of(row.getStandardApiId()),
                List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE));
            return new StandardApiDetailView(row.getStandardApiCode(), row.getStandardApiName(),
                row.getDescription(), support.definitions(fieldRows, ApiFieldDirection.STANDARD_API_REQUEST),
                support.definitions(fieldRows, ApiFieldDirection.STANDARD_API_RESPONSE), implementers(row),
                row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getUpdatedBy());
        });
    }

    /** 实现该标准 API 的渠道，按编码排序。 */
    List<ConfigurationReference> implementers(StandardApi row) {
        var ids = implementations.allByStandardApi(row.getStandardApiId()).stream()
            .map(ChannelImplementation::getChannelId).toList();
        return channels.byIds(ids).stream()
            .map(item -> new ConfigurationReference(item.getChannelCode(), item.getChannelName()))
            .sorted(Comparator.comparing(ConfigurationReference::getCode)).toList();
    }
}
