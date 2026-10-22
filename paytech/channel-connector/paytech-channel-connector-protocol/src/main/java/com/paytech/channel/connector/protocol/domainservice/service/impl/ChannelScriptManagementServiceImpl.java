package com.paytech.channel.connector.protocol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationScriptContext;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelScriptManagementService;

import lombok.AllArgsConstructor;

/** 公共脚本的本地保存，版本保护由 Repository 条件更新完成。 */
@Service
@AllArgsConstructor
public class ChannelScriptManagementServiceImpl implements ChannelScriptManagementService {

    private final ChannelScriptRepository channelScripts;

    /** 插入新脚本，渠道内编码唯一索引是最终保护。 */
    @Override
    public void create(ChannelScript integrationScript) {
        channelScripts.insert(integrationScript);
    }

    /** 按原版本整体更新可编辑字段。 */
    @Override
    public void update(UpdateIntegrationScriptContext context) {
        channelScripts.update(context.getIntegrationScript(), context.getName(), context.getStage(),
            context.getParametersJson(), context.getSourceCode(), context.getDescription(), context.getUpdatedBy(),
            context.getUpdateTime());
    }

    /** 按原版本删除。 */
    @Override
    public void delete(ChannelScript integrationScript) {
        channelScripts.delete(integrationScript);
    }
}
