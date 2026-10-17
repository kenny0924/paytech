package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.CreateApiScopeContext;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.UpdateApiScopeContext;

/** 在调用方本地事务中保存已校验的 Scope 目录变更。 */
public interface ApiScopeManagementService {

    /** 保存完整的新目录对象，唯一编码由数据库最终仲裁。 */
    void create(CreateApiScopeContext context);

    /** 保存名称和说明，并由 Repository 同步原对象版本与时间。 */
    void update(UpdateApiScopeContext context);
}
