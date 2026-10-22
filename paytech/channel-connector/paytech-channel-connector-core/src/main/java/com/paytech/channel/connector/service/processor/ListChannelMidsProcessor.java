package com.paytech.channel.connector.service.processor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.domain.ChannelMidListCriteria;
import com.paytech.channel.connector.domain.ChannelMidSummaryView;
import com.paytech.channel.connector.domainservice.service.ChannelMidQueryService;
import com.paytech.channel.connector.protocol.domain.ChannelIdentity;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * MID 分页列表：本模块读取 MID、当前账号名称与能力组计数，渠道编码经 protocol 渠道服务按当页渠道补齐（跨模块不 JOIN）
 * <li>只读、不建事务。</li>
 */
@Component
@AllArgsConstructor
public class ListChannelMidsProcessor
    extends AbstractProcessor<ChannelMidListCriteria, PagingResponseBody<ChannelMidSummaryView>> {
    private final ChannelService channelService;
    private final ChannelMidQueryService channelMidQueryService;

    /** 分页读取 MID 并补齐渠道编码。 */
    @Override
    protected PagingResponseBody<ChannelMidSummaryView> processRequest(ChannelMidListCriteria request) {
        PagingResponseBody<ChannelMidSummaryView> page = channelMidQueryService.listMids(request);
        Map<Long, ChannelIdentity> channels = channelService.findChannels(
            page.items().stream().map(ChannelMidSummaryView::getChannelId).distinct().toList()).stream()
            .collect(Collectors.toMap(ChannelIdentity::getChannelId, Function.identity()));
        page.items().forEach(item -> {
            ChannelIdentity channel = channels.get(item.getChannelId());
            item.setChannelCode(channel == null ? null : channel.getChannelCode());
        });
        return page;
    }
}
