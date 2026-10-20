package com.paytech.channel.connector.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** MID 能力组的本地保存与查询；写入与变更记录同事务提交。 */
@Service
@AllArgsConstructor
public class MidCapabilityServiceImpl implements MidCapabilityService {
    private final ChannelProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final MidCapabilityRepository midCapabilityRepository;
    private final ChannelConfigChangeRepository channelConfigChangeRepository;
    private final MidCapabilityDetailRepository midCapabilityDetailRepository;

    /** 已注册的能力目录，按能力代码升序；每次读取当前配置。 */
    @Override
    public SortedMap<String, CapabilityDefinition> capabilityDefinitions() {
        return new TreeMap<>(properties.getCapabilityDefinitions());
    }

    /** 读取能力组；不存在时业务拒绝。 */
    @Override
    public MidCapability loadCapability(long midCapabilityId) {
        MidCapability capability = midCapabilityRepository.findById(midCapabilityId);
        if (capability == null) {
            throw new BizCheckFailException("MID 能力组不存在: " + midCapabilityId);
        }
        return capability;
    }

    /** 能力组与其明细，明细按能力代码升序。 */
    @Override
    public MidCapabilityView getCapability(long midCapabilityId) {
        MidCapability capability = loadCapability(midCapabilityId);
        List<MidCapabilityDetail> details = midCapabilityDetailRepository.findByCapabilityIds(List.of(midCapabilityId))
            .stream().sorted(Comparator.comparing(MidCapabilityDetail::getCapabilityCode)).toList();
        return new MidCapabilityView(capability, details);
    }

    /** 写入能力组、明细与变更记录。 */
    @Override
    public void createCapability(MidCapabilityWriteContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            midCapabilityRepository.insert(context.getCapability());
            midCapabilityDetailRepository.insertAll(context.getDetails());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 删旧插新并写入变更记录。 */
    @Override
    public void replaceDetails(MidCapabilityWriteContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            midCapabilityRepository.touch(context.getCapability(), context.getNow());
            midCapabilityDetailRepository.deleteByCapabilityId(context.getCapability().getMidCapabilityId());
            midCapabilityDetailRepository.insertAll(context.getDetails());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 启停并写入变更记录。 */
    @Override
    public void changeCapabilityStatus(MidCapabilityWriteContext context, CapabilityStatus targetStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            midCapabilityRepository.updateStatus(context.getCapability(), targetStatus, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }
}
