package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.CreateApiScopeContext;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.UpdateApiScopeContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ApiScopeManagementService;

import lombok.AllArgsConstructor;

/** 只保存本库 Scope 目录事实，授权和业务查询由 Processor 完成。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ApiScopeManagementServiceImpl implements ApiScopeManagementService {

    private final ApiScopeRepository repository;

    /** 参与已有本地事务插入目录，不调用外部依赖。 */
    @Override
    public void create(CreateApiScopeContext context) {
        repository.insert(context.getScope());
    }

    /** 参与已有本地事务保存编辑，不重新查询或重建原目录对象。 */
    @Override
    public void update(UpdateApiScopeContext context) {
        repository.update(context.getScope(), context.getDisplayName(), context.getDescription(),
            context.getUpdateTime());
    }
}
