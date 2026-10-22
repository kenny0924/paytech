package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.ImplementationSummaryView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E23 渠道标准 API 实现列表：读取渠道全部实现，从已保存流程提取节点、出口、初始化项与预算摘要。
 *
 * <p>
 * 数量受标准 API 总数限制，不分页；普通列表不开显式事务。
 */
@Component
@AllArgsConstructor
public class ListImplementationsProcessor
    extends AbstractProcessor<GetExternalApiConfigRequest, List<ImplementationSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final StandardApiRepository standardApis;
    private final ChannelImplementationRepository implementations;

    @Override
    protected List<ImplementationSummaryView> processRequest(GetExternalApiConfigRequest request) {
        long channelId = children.channel(request.getCode()).getChannelId();
        var rows = implementations.allByChannel(channelId);
        Map<Long, StandardApi> standardApiById =
            standardApis.byIds(rows.stream().map(ChannelImplementation::getStandardApiId).toList()).stream()
                .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
        return rows.stream().filter(row -> standardApiById.containsKey(row.getStandardApiId()))
            .map(row -> summary(row, standardApiById.get(row.getStandardApiId())))
            .sorted(Comparator.comparing(ImplementationSummaryView::getStandardApiCode)).toList();
    }

    /** 从已保存流程提取摘要；流程在保存时已通过结构校验。 */
    ImplementationSummaryView summary(ChannelImplementation row, StandardApi standardApi) {
        var flow = codec.tree(row.getFlowDefinitionJson());
        var initializerIds = new ArrayList<String>();
        flow.path("initializers").forEach(item -> initializerIds.add(item.path("id").asString("")));
        return new ImplementationSummaryView(standardApi.getStandardApiCode(), standardApi.getStandardApiName(),
            flow.path("nodes").size(), flow.path("returns").size(), initializerIds, flow.path("timeoutMs").asInt(),
            row.getVersion(), row.getUpdateTime());
    }
}
