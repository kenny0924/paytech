package com.paytech.channel.connector.protocol.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationScript;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationScriptContext;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationScriptManagementService;

import lombok.AllArgsConstructor;

/** 公共脚本的本地保存，版本保护由 Repository 条件更新完成。 */
@Service
@AllArgsConstructor
public class IntegrationScriptManagementServiceImpl implements IntegrationScriptManagementService {

    private final IntegrationScriptRepository integrationScripts;

    /** 插入新脚本，接入方内编码唯一索引是最终保护。 */
    @Override
    public void create(IntegrationScript integrationScript) {
        integrationScripts.insert(integrationScript);
    }

    /** 按原版本整体更新可编辑字段。 */
    @Override
    public void update(UpdateIntegrationScriptContext context) {
        integrationScripts.update(context.getIntegrationScript(), context.getName(), context.getStage(),
            context.getParametersJson(), context.getSourceCode(), context.getDescription(), context.getUpdatedBy(),
            context.getUpdateTime());
    }

    /** 按原版本删除。 */
    @Override
    public void delete(IntegrationScript integrationScript) {
        integrationScripts.delete(integrationScript);
    }
}
