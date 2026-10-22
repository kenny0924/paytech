package com.paytech.channel.connector.service.processor;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.service.processor.request.ListChannelsRequest;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 渠道列表：protocol 渠道服务按类型、状态与关键字分页（含出站地址数、ACTIVE 授权数），本模块按当页渠道补齐账号与 MID 计数
 * <li>渠道身份归 protocol、账号与 MID 归 core，跨模块不 JOIN；只读、不建事务，各计数观察相邻已提交状态即可。</li>
 */
@Component
@AllArgsConstructor
public class ListChannelsProcessor extends AbstractProcessor<ListChannelsRequest, PagingResponseBody<ChannelSummaryView>> {
    private final ChannelService channelService;
    private final ChannelMidRepository channelMidRepository;
    private final ChannelAccountRepository channelAccountRepository;

    /** 分页读取渠道并补齐计数。 */
    @Override
    protected PagingResponseBody<ChannelSummaryView> processRequest(ListChannelsRequest request) {
        PagingResponseBody<ChannelSummary> page = channelService.listChannels(request.getChannelType(),
            request.getStatus(), request.getKeyword(), request.getPaging());
        var channelIds = page.items().stream().map(ChannelSummary::getChannelId).toList();
        Map<Long, Long> accounts = channelAccountRepository.countByChannels(channelIds);
        Map<Long, Long> mids = channelMidRepository.countByChannels(channelIds);
        return page.map(channel -> new ChannelSummaryView(channel,
            accounts.getOrDefault(channel.getChannelId(), 0L), mids.getOrDefault(channel.getChannelId(), 0L)));
    }
}
