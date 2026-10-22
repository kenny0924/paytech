package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.domain.UpdateImplementationContext;

/** 渠道标准 API 实现的本地保存；参与调用方事务，不重复校验已确认的关系。 */
public interface ChannelImplementationManagementService {

    /** 插入新实现。 */
    void create(ChannelImplementation implementation);

    /** 按原版本替换流程定义。 */
    void update(UpdateImplementationContext context);

    /** 按原版本删除实现。 */
    void delete(ChannelImplementation implementation);
}
