package com.paytech.platform.support.integration.domainservice.service;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.domain.CreateIntegrationApiContext;
import com.paytech.platform.support.integration.domain.UpdateIntegrationApiContext;

/** 原子 API 及其字段的本地保存；参与调用方事务，不重复校验已确认的关系。 */
public interface IntegrationApiManagementService {

    /** 插入原子 API 与全部字段。 */
    void create(CreateIntegrationApiContext context);

    /** 按原版本更新主表并整组替换字段。 */
    void update(UpdateIntegrationApiContext context);

    /** 删除全部字段后按原版本删除原子 API。 */
    void delete(IntegrationApi integrationApi);
}
