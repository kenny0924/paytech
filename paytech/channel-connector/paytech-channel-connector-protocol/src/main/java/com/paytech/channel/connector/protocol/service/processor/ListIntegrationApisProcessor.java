package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationApiSummaryView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/**
 * E11 原子 API 列表：分页读取后从已保存定义提取传输与脚本摘要，并扫描本渠道流程得到引用方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
public class ListIntegrationApisProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<IntegrationApiSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final StandardApiRepository standardApis;
    private final ChannelApiRepository channelApis;
    private final ChannelImplementationRepository implementations;

    @Override
    protected PagingResponseBody<IntegrationApiSummaryView> processRequest(ListIntegrationChildrenRequest request) {
        long channelId = children.channel(request.getIntegrationCode()).getChannelId();
        var paging = request.getPaging();
        var rows = channelApis.list(channelId, request.getKeyword(), paging.offset(), paging.size());
        long total = channelApis.count(channelId, request.getKeyword());
        var flows = implementations.allByChannel(channelId);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(flows.stream()
            .map(ChannelImplementation::getStandardApiId).toList()).stream()
            .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
        var items = rows.stream().map(row -> summary(row, flows, standardApiById)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 从已保存定义提取摘要，引用方按标准 API 编码排序。 */
    IntegrationApiSummaryView summary(ChannelApi row, List<ChannelImplementation> flows,
        Map<Long, StandardApi> standardApiById) {
        var definition = codec.tree(row.getDefinitionJson());
        var transport = definition.path("transport");
        int requestScripts = 0;
        int responseScripts = 0;
        for (var script : definition.path("scripts")) {
            if ("REQUEST".equals(script.path("stage").asString(""))) {
                requestScripts++;
            } else {
                responseScripts++;
            }
        }
        var referencedBy = flows.stream()
            .filter(flow -> !codec.nodesUsing(flow.getFlowDefinitionJson(), row.getApiCode()).isEmpty())
            .map(flow -> standardApiById.get(flow.getStandardApiId()).getStandardApiCode()).sorted().toList();
        return new IntegrationApiSummaryView(row.getApiCode(), row.getApiName(), transport.path("method").asString(""),
            transport.path("endpointCode").asString(""), transport.path("pathTemplate").asString(""), requestScripts,
            responseScripts, referencedBy, row.getVersion(), row.getUpdateTime());
    }
}
