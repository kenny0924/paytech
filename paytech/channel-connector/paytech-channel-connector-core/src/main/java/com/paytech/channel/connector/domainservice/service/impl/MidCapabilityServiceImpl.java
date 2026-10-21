package com.paytech.channel.connector.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.core.configuration.CapabilityDefinition;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.enums.CapabilityStatus;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domain.MidCapabilityWriteContext;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.MidCapabilityService;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** MID 能力组的本地保存与查询；写入方法加入调用方 Processor 的事务，变更记录由 Processor 在同一事务中写入。 */
@Service
@AllArgsConstructor
public class MidCapabilityServiceImpl implements MidCapabilityService {
    private final ChannelProperties properties;
    private final MidCapabilityRepository midCapabilityRepository;
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

    /** 写入能力组、明细；须在调用方事务内执行。 */
    @Override
    public void createCapability(MidCapabilityWriteContext context) {
        midCapabilityRepository.insert(context.getCapability());
        midCapabilityDetailRepository.insertAll(context.getDetails());
    }

    /** 删旧插新；须在调用方事务内执行。 */
    @Override
    public void replaceDetails(MidCapabilityWriteContext context) {
        midCapabilityRepository.touch(context.getCapability(), context.getNow());
        midCapabilityDetailRepository.deleteByCapabilityId(context.getCapability().getMidCapabilityId());
        midCapabilityDetailRepository.insertAll(context.getDetails());
    }

    /** 启停；须在调用方事务内执行。 */
    @Override
    public void changeCapabilityStatus(MidCapabilityWriteContext context, CapabilityStatus targetStatus) {
        midCapabilityRepository.updateStatus(context.getCapability(), targetStatus, context.getNow());
    }
}
