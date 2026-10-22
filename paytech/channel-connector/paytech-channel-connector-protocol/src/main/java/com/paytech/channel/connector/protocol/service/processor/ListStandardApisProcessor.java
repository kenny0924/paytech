package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ConfigurationReference;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.StandardApiSummaryView;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E01 标准 API 列表：分页读取主表后批量组合字段数量与实现方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
public class ListStandardApisProcessor
    extends AbstractProcessor<ListExternalApiConfigRequest, PagingResponseBody<StandardApiSummaryView>> {

    private final ApiFieldRepository fields;
    private final ChannelRepository channels;
    private final StandardApiRepository standardApis;
    private final ChannelImplementationRepository implementations;

    @Override
    protected PagingResponseBody<StandardApiSummaryView> processRequest(ListExternalApiConfigRequest request) {
        var paging = request.getPaging();
        List<StandardApi> rows = standardApis.list(request.getKeyword(), paging.offset(), paging.size());
        long total = standardApis.count(request.getKeyword());
        List<Long> ids = rows.stream().map(StandardApi::getStandardApiId).toList();
        List<ApiField> fieldRows = ids.isEmpty() ? List.of() : fields.byApis(ids,
            List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE));
        List<ChannelImplementation> implementationRows = implementations.allByStandardApis(ids);
        Map<Long, Channel> implementers = integrationsById(implementationRows);
        var items = rows.stream().map(row -> summary(row, fieldRows, implementationRows, implementers)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 批量读取实现方渠道，空集合不查询。 */
    Map<Long, Channel> integrationsById(List<ChannelImplementation> rows) {
        var ids = rows.stream().map(ChannelImplementation::getChannelId).distinct().toList();
        return channels.byIds(ids).stream()
            .collect(Collectors.toMap(Channel::getChannelId, Function.identity()));
    }

    /** 组合单个列表项；实现方按渠道编码排序。 */
    StandardApiSummaryView summary(StandardApi row, List<ApiField> fieldRows,
        List<ChannelImplementation> implementationRows, Map<Long, Channel> implementers) {
        long id = row.getStandardApiId();
        int requestCount = (int) fieldRows.stream()
            .filter(field -> field.getApiId() == id && field.getDirection() == ApiFieldDirection.STANDARD_API_REQUEST)
            .count();
        int responseCount = (int) fieldRows.stream()
            .filter(field -> field.getApiId() == id && field.getDirection() == ApiFieldDirection.STANDARD_API_RESPONSE)
            .count();
        var implementedBy = implementationRows.stream().filter(item -> item.getStandardApiId() == id)
            .map(item -> implementers.get(item.getChannelId()))
            .map(integration -> new ConfigurationReference(integration.getChannelCode(),
                integration.getChannelName()))
            .sorted(java.util.Comparator.comparing(ConfigurationReference::getCode)).toList();
        return new StandardApiSummaryView(row.getStandardApiCode(), row.getStandardApiName(), requestCount,
            responseCount, implementedBy, row.getVersion(), row.getUpdateTime());
    }
}
