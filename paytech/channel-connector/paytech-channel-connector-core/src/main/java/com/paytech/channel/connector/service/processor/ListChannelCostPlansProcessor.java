package com.paytech.channel.connector.service.processor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.domain.ChannelCostPlanSummaryView;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.ListChannelCostPlansRequest;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 渠道成本方案分页列表：本模块读取方案、当前版本与待复核数，渠道编码与名称经 protocol 渠道服务按当页渠道补齐（跨模块不 JOIN）
 * <li>只读、不建事务。</li>
 */
@Component
@AllArgsConstructor
public class ListChannelCostPlansProcessor
    extends AbstractProcessor<ListChannelCostPlansRequest, PagingResponseBody<ChannelCostPlanSummaryView>> {
    private final ChannelService channelService;
    private final ChannelCostPlanService channelCostPlanService;

    /** 分页读取方案并补齐渠道编码与名称。 */
    @Override
    protected PagingResponseBody<ChannelCostPlanSummaryView> processRequest(ListChannelCostPlansRequest request) {
        PagingResponseBody<ChannelCostPlanSummaryView> page =
            channelCostPlanService.findPage(request.getChannelId(), request.getStatus(), request.getPaging());
        Map<Long, ChannelIdentity> channels = channelService.findChannels(
            page.items().stream().map(ChannelCostPlanSummaryView::getChannelId).distinct().toList()).stream()
            .collect(Collectors.toMap(ChannelIdentity::getChannelId, Function.identity()));
        page.items().forEach(item -> {
            ChannelIdentity channel = channels.get(item.getChannelId());
            item.setChannelCode(channel == null ? null : channel.getChannelCode());
            item.setChannelName(channel == null ? null : channel.getChannelName());
        });
        return page;
    }
}
