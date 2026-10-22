package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.IntegrationScriptSummaryView;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/**
 * E17 公共脚本列表：可按执行阶段筛选，分页读取后扫描本渠道原子 API 定义得到引用方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
public class ListIntegrationScriptsProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<IntegrationScriptSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ChannelApiRepository channelApis;
    private final ChannelScriptRepository channelScripts;

    @Override
    protected PagingResponseBody<IntegrationScriptSummaryView> processRequest(ListIntegrationChildrenRequest request) {
        long channelId = children.channel(request.getIntegrationCode()).getChannelId();
        var paging = request.getPaging();
        var rows = channelScripts.list(channelId, request.getKeyword(), request.getStage(), paging.offset(),
            paging.size());
        long total = channelScripts.count(channelId, request.getKeyword(), request.getStage());
        var apis = rows.isEmpty() ? List.<ChannelApi>of() : channelApis.allByChannel(channelId);
        var items = rows.stream().map(row -> summary(row, apis)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 组合单个列表项；引用方按原子 API 编码排序。 */
    IntegrationScriptSummaryView summary(ChannelScript row, List<ChannelApi> apis) {
        var referencedBy = apis.stream()
            .filter(api -> codec.scriptCodes(api.getDefinitionJson()).contains(row.getScriptCode()))
            .map(ChannelApi::getApiCode).sorted().toList();
        var names = codec.parameters(row.getParameterDefinition()).stream().map(ParameterDeclaration::getName).toList();
        return new IntegrationScriptSummaryView(row.getScriptCode(), row.getScriptName(), row.getExecutionStage(),
            names, referencedBy, row.getVersion(), row.getUpdateTime());
    }
}
