package com.paytech.platform.support.integration.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.domain.UpdateIntegrationContext;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.service.IntegrationManagementService;

import lombok.AllArgsConstructor;

/** 接入方主表的本地保存，版本保护由 Repository 条件更新完成。 */
@Service
@AllArgsConstructor
public class IntegrationManagementServiceImpl implements IntegrationManagementService {

    private final IntegrationRepository integrations;

    /** 插入新接入方，编码唯一索引是最终保护。 */
    @Override
    public void create(Integration integration) {
        integrations.insert(integration);
    }

    /** 按原版本更新可编辑字段。 */
    @Override
    public void update(UpdateIntegrationContext context) {
        integrations.update(context.getIntegration(), context.getName(), context.getDescription(),
            context.getEndpointsJson(), context.getUpdatedBy(), context.getUpdateTime());
    }

    /** 按原版本删除。 */
    @Override
    public void delete(Integration integration) {
        integrations.delete(integration);
    }
}
