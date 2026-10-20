package com.paytech.channel.connector.domainservice.service.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.dal.dataobject.MidCapability;
import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelMidContext;
import com.paytech.channel.connector.domain.ChannelMidDetailView;
import com.paytech.channel.connector.domain.MidCapabilityView;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityDetailRepository;
import com.paytech.channel.connector.domainservice.repository.MidCapabilityRepository;
import com.paytech.channel.connector.domainservice.service.ChannelMidService;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.AllArgsConstructor;

/** MID 的本地保存与查询；写入与变更记录同事务提交。 */
@Service
@AllArgsConstructor
public class ChannelMidServiceImpl implements ChannelMidService {
    private final ChannelMidRepository channelMidRepository;
    private final TransactionTemplate transactionTemplate;
    private final MidCapabilityRepository midCapabilityRepository;
    private final ChannelConfigChangeRepository channelConfigChangeRepository;
    private final MidCapabilityDetailRepository midCapabilityDetailRepository;

    /** 读取 MID；不存在时业务拒绝。 */
    @Override
    public ChannelMid loadMid(long midId) {
        ChannelMid mid = channelMidRepository.findById(midId);
        if (mid == null) {
            throw new BizCheckFailException("MID 不存在: " + midId);
        }
        return mid;
    }

    /** MID 与其能力组、明细；明细按能力代码升序归入所属组。 */
    @Override
    public ChannelMidDetailView getMid(long midId) {
        ChannelMid mid = loadMid(midId);
        List<MidCapability> capabilities = midCapabilityRepository.findByMidIds(List.of(midId));
        List<MidCapabilityDetail> details = midCapabilityDetailRepository.findByCapabilityIds(
            capabilities.stream().map(MidCapability::getMidCapabilityId).toList());
        return new ChannelMidDetailView(mid, capabilities.stream()
            .map(capability -> new MidCapabilityView(capability, detailsOf(capability, details))).toList());
    }

    /** 写入新 MID 与变更记录。 */
    @Override
    public void createMid(ChannelMidContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            channelMidRepository.insert(context.getMid());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 改绑并写入变更记录。 */
    @Override
    public void rebindAccount(ChannelMidContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            channelMidRepository.updateAccount(context.getMid(), context.getAccount().getChannelAccountId(),
                context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 变更状态、激活 / 关闭时间并写入变更记录。 */
    @Override
    public void changeMidStatus(ChannelMidContext context, ChannelStatus targetStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            channelMidRepository.updateStatus(context.getMid(), targetStatus, context.activatedAtAfter(targetStatus),
                context.closedAtAfter(targetStatus), context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 属于能力组的明细，按能力代码升序。 */
    static List<MidCapabilityDetail> detailsOf(MidCapability capability, List<MidCapabilityDetail> details) {
        return details.stream().filter(detail -> detail.getMidCapabilityId().equals(capability.getMidCapabilityId()))
            .sorted(Comparator.comparing(MidCapabilityDetail::getCapabilityCode)).toList();
    }
}
