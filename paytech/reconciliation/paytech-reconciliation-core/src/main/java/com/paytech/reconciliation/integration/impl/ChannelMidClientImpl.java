package com.paytech.reconciliation.integration.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.channel.ChannelMidApi;
import com.paytech.channel.connector.api.channel.response.ChannelMidResponseBody;
import com.paytech.channel.connector.api.channel.response.ChannelMidSummaryResponseBody;
import com.paytech.channel.connector.api.channel.response.MidCapabilityResponseBody;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.domain.MidProfile;
import com.paytech.reconciliation.integration.ChannelMidClient;

import lombok.AllArgsConstructor;

/**
 * channel-connector {@link ChannelMidApi} 适配：只调用列表与详情两个查询
 * <li>MID 详情没有渠道编码，只有 channelId；渠道编码按「经营主体 + 状态 + 外部 MID 编码」查列表摘要，取同一 MID 主键的一条。</li>
 * <li>对方 4xx 业务拒绝（MID 不存在等）转为「未找到」，由调用方给出带上下文的业务拒绝；网络与 5xx 由公共出站边界转为系统错误。</li>
 */
@Component
@AllArgsConstructor
public class ChannelMidClientImpl implements ChannelMidClient {
    /** MID 状态：已激活。 */
    static final String ACTIVE = "ACTIVE";

    /** 列表分页的最大条数。 */
    static final int PAGE_SIZE = 200;

    private final ChannelMidApi channelMidApi;

    /** 读取 MID 详情并补齐渠道编码；对方业务拒绝时返回 null。 */
    @Override
    public MidProfile findMid(long midId) {
        ChannelMidResponseBody detail;
        try {
            detail = channelMidApi.getChannelMid(String.valueOf(midId));
        } catch (FailException rejected) {
            return null;
        }
        PagingResponseBody<ChannelMidSummaryResponseBody> page = channelMidApi.listChannelMids(null, null,
            detail.getEntityNo(), detail.getStatus(), detail.getExternalMidCode(), 1, PAGE_SIZE);
        String channelCode = page.items().stream().filter(item -> detail.getMidId().equals(item.getMidId()))
            .map(ChannelMidSummaryResponseBody::getChannelCode).findFirst().orElse(null);
        return profile(detail, channelCode);
    }

    /** 逐页读取 ACTIVE MID 摘要，再逐个读取详情取能力组币种。 */
    @Override
    public List<MidProfile> listActiveMids(String entityNo) {
        List<MidProfile> profiles = new ArrayList<>();
        for (ChannelMidSummaryResponseBody summary : listActiveSummaries(entityNo)) {
            ChannelMidResponseBody detail = channelMidApi.getChannelMid(summary.getMidId());
            profiles.add(profile(detail, summary.getChannelCode()));
        }
        profiles.sort(Comparator.comparingLong(MidProfile::getMidId));
        return profiles;
    }

    /** 逐页读取 ACTIVE MID 摘要，直到读完总数。 */
    List<ChannelMidSummaryResponseBody> listActiveSummaries(String entityNo) {
        List<ChannelMidSummaryResponseBody> summaries = new ArrayList<>();
        int page = 1;
        while (true) {
            PagingResponseBody<ChannelMidSummaryResponseBody> result = channelMidApi.listChannelMids(null, null,
                entityNo, ACTIVE, null, page, PAGE_SIZE);
            summaries.addAll(result.items());
            if (result.items().isEmpty() || summaries.size() >= result.total()) {
                return summaries;
            }
            page++;
        }
    }

    /** 由详情与渠道编码组装对账侧 MID 事实。 */
    static MidProfile profile(ChannelMidResponseBody detail, String channelCode) {
        Set<String> currencies = new LinkedHashSet<>();
        if (detail.getCapabilities() != null) {
            for (MidCapabilityResponseBody capability : detail.getCapabilities()) {
                currencies.add(capability.getCurrency());
            }
        }
        return new MidProfile(Long.parseLong(detail.getMidId()), detail.getExternalMidCode(), channelCode,
            detail.getEntityNo(), detail.getStatus(), Set.copyOf(currencies));
    }
}
