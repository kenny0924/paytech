package com.paytech.channel.connector.domainservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.domain.OperatingEntityMids;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.OperatingEntityMidService;

import lombok.AllArgsConstructor;

/** 批量读取某 OE 的账号、MID 与能力；只依赖本模块仓储。 */
@Service
@AllArgsConstructor
public class OperatingEntityMidServiceImpl implements OperatingEntityMidService {
    private final ChannelMidRepository channelMidRepository;
    private final MidCapabilityRepository midCapabilityRepository;
    private final ChannelAccountRepository channelAccountRepository;
    private final MidCapabilityDetailRepository midCapabilityDetailRepository;

    /** 账号只保留 OE 精确相等的；能力读取全部 MID 的，可用筛选由调用方完成。 */
    @Override
    public OperatingEntityMids loadMids(String entityNo) {
        Map<Long, ChannelAccount> accounts = channelAccountRepository.findByEntity(entityNo).stream()
            .filter(account -> entityNo.equals(account.getEntityNo()))
            .collect(Collectors.toMap(ChannelAccount::getChannelAccountId, Function.identity()));
        List<ChannelMid> mids = channelMidRepository.findByAccountIds(List.copyOf(accounts.keySet()));
        List<MidCapability> scopes = midCapabilityRepository.findByMidIds(mids.stream().map(ChannelMid::getMidId).toList());
        return new OperatingEntityMids(entityNo, accounts, mids, scopes, midCapabilityDetailRepository.findByCapabilityIds(
            scopes.stream().map(MidCapability::getMidCapabilityId).toList()));
    }
}
