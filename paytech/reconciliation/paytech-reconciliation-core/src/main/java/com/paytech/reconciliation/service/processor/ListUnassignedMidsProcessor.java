package com.paytech.reconciliation.service.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.domain.MidProfile;
import com.paytech.reconciliation.domain.UnassignedMid;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.integration.ChannelMidClient;
import com.paytech.reconciliation.service.processor.request.ListUnassignedMidsRequest;

import lombok.AllArgsConstructor;

/**
 * 查询已激活但尚未归入清算账户的 MID + 币种（UC-R17，#5）
 * <li>= channel-connector ACTIVE MID × 能力组币种 − 已归属；只读，不开启事务，先读 channel-connector 再读本库归属。</li>
 * <li>按渠道编码、经营主体、MID 主键、币种升序。</li>
 */
@Component
@AllArgsConstructor
public class ListUnassignedMidsProcessor extends AbstractProcessor<ListUnassignedMidsRequest, List<UnassignedMid>> {
    private final ChannelMidClient channelMidClient;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    @Override
    protected List<UnassignedMid> processRequest(ListUnassignedMidsRequest request) {
        List<MidProfile> mids = channelMidClient.listActiveMids(request.getEntityNo()).stream()
            .filter(mid -> request.getChannelCode() == null || request.getChannelCode().equals(mid.getChannelCode()))
            .toList();
        Set<String> assigned = clearingAccountMidRepository.listByMidIds(mids.stream().map(MidProfile::getMidId)
            .toList()).stream().map(mid -> key(mid.getMidId(), mid.getCurrency())).collect(Collectors.toSet());
        List<UnassignedMid> result = new ArrayList<>();
        for (MidProfile mid : mids) {
            mid.getCurrencies().stream()
                .filter(currency -> request.getCurrency() == null || request.getCurrency().equals(currency))
                .filter(currency -> !assigned.contains(key(mid.getMidId(), currency)))
                .forEach(currency -> result.add(new UnassignedMid(mid, currency)));
        }
        result.sort(Comparator.comparing((UnassignedMid item) -> String.valueOf(item.getMid().getChannelCode()))
            .thenComparing(item -> item.getMid().getEntityNo()).thenComparingLong(item -> item.getMid().getMidId())
            .thenComparing(UnassignedMid::getCurrency));
        return result;
    }

    /** 归属键：MID + 币种。 */
    static String key(long midId, String currency) {
        return midId + ":" + currency;
    }
}
