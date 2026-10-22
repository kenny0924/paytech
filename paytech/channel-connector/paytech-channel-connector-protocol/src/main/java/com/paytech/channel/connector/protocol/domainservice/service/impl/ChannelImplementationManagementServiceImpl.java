package com.paytech.channel.connector.protocol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.domain.UpdateImplementationContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelImplementationManagementService;

import lombok.AllArgsConstructor;

/** 渠道标准 API 实现单表保存，唯一索引与版本 CAS 是最终保护。 */
@Service
@AllArgsConstructor
public class ChannelImplementationManagementServiceImpl implements ChannelImplementationManagementService {

    private final ChannelImplementationRepository implementations;

    /** 同一渠道与标准 API 重复时由唯一索引拒绝。 */
    @Override
    public void create(ChannelImplementation implementation) {
        implementations.insert(implementation);
    }

    /** 按原版本更新，成功后同步行内版本。 */
    @Override
    public void update(UpdateImplementationContext context) {
        implementations.update(context.getImplementation(), context.getFlowDefinitionJson(), context.getUpdatedBy(),
            context.getUpdateTime());
    }

    /** 物理删除，不保留历史版本。 */
    @Override
    public void delete(ChannelImplementation implementation) {
        implementations.delete(implementation);
    }
}
