package com.paytech.channel.connector.protocol.service.processor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ConfigurationReference;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationSummaryView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E06 接入方列表：分页读取主表后批量组合原子 API、脚本数量与已实现的标准 API。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
public class ListIntegrationsProcessor
    extends AbstractProcessor<ListExternalApiConfigRequest, PagingResponseBody<IntegrationSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationScriptRepository integrationScripts;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected PagingResponseBody<IntegrationSummaryView> processRequest(ListExternalApiConfigRequest request) {
        var paging = request.getPaging();
        List<Integration> rows = integrations.list(request.getKeyword(), paging.offset(), paging.size());
        long total = integrations.count(request.getKeyword());
        List<Long> ids = rows.stream().map(Integration::getIntegrationId).toList();
        Map<Long, Long> apiCounts = integrationApis.countByIntegrations(ids);
        Map<Long, Long> scriptCounts = integrationScripts.countByIntegrations(ids);
        List<IntegrationImplementation> implementationRows = implementations.allByIntegrations(ids);
        Map<Long, StandardApi> standardApiById = standardApisById(implementationRows);
        var items = rows.stream().map(row -> summary(row, apiCounts, scriptCounts, implementationRows,
            standardApiById)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 批量读取被实现的标准 API，空集合不查询。 */
    Map<Long, StandardApi> standardApisById(List<IntegrationImplementation> rows) {
        var ids = rows.stream().map(IntegrationImplementation::getStandardApiId).distinct().toList();
        return standardApis.byIds(ids).stream()
            .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
    }

    /** 组合单个列表项；已实现的标准 API 按编码排序。 */
    IntegrationSummaryView summary(Integration row, Map<Long, Long> apiCounts, Map<Long, Long> scriptCounts,
        List<IntegrationImplementation> implementationRows, Map<Long, StandardApi> standardApiById) {
        long id = row.getIntegrationId();
        var implemented = implementationRows.stream().filter(item -> item.getIntegrationId() == id)
            .map(item -> standardApiById.get(item.getStandardApiId()))
            .map(api -> new ConfigurationReference(api.getStandardApiCode(), api.getStandardApiName()))
            .sorted(Comparator.comparing(ConfigurationReference::getCode)).toList();
        return new IntegrationSummaryView(row.getIntegrationCode(), row.getIntegrationName(),
            codec.endpoints(row.getEndpoints()).size(), apiCounts.getOrDefault(id, 0L),
            scriptCounts.getOrDefault(id, 0L), implemented, row.getVersion(), row.getUpdateTime());
    }
}
