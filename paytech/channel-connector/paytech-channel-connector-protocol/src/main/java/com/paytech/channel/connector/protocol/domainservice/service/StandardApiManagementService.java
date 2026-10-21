package com.paytech.channel.connector.protocol.domainservice.service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.CreateStandardApiContext;
import com.paytech.channel.connector.protocol.domain.UpdateStandardApiContext;

/** 标准 API 及其字段的本地保存；参与调用方事务，不重复校验已确认的关系。 */
public interface StandardApiManagementService {

    /** 插入标准 API 与全部字段。 */
    void create(CreateStandardApiContext context);

    /** 按原版本更新主表并整组替换字段。 */
    void update(UpdateStandardApiContext context);

    /** 删除全部字段后按原版本删除标准 API。 */
    void delete(StandardApi standardApi);
}
