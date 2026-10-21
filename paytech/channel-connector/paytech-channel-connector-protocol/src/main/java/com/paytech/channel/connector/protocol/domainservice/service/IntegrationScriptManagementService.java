package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationScript;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationScriptContext;

/** 公共脚本的本地保存；参与调用方事务，不重复校验已确认的关系。 */
public interface IntegrationScriptManagementService {

    /** 插入脚本。 */
    void create(IntegrationScript integrationScript);

    /** 按原版本整体更新。 */
    void update(UpdateIntegrationScriptContext context);

    /** 按原版本删除；未被引用已由调用方确认。 */
    void delete(IntegrationScript integrationScript);
}
