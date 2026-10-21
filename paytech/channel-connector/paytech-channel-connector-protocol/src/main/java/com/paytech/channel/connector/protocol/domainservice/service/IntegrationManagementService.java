package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.domain.UpdateIntegrationContext;

/** 外部接入方的本地保存；参与调用方事务，不重复校验已确认的关系。 */
public interface IntegrationManagementService {

    /** 插入接入方。 */
    void create(Integration integration);

    /** 按原版本更新名称、说明与出站地址。 */
    void update(UpdateIntegrationContext context);

    /** 按原版本删除接入方；下级对象为空已由调用方确认。 */
    void delete(Integration integration);
}
