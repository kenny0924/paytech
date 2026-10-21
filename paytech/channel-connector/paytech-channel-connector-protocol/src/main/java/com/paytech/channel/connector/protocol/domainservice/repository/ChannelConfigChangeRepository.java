package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** t_channel_config_change：变更留痕只增不改；写入必须位于调用方的业务事务内。 */
public interface ChannelConfigChangeRepository {

    /** 写入一条已准备好主键与创建时间的变更记录；行数不为 1 时按系统错误失败。 */
    void insert(ChannelConfigChange change);

    /** 按被变更对象分页读取，按主键降序；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件。 */
    PagingResponseBody<ChannelConfigChange> findPage(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging);
}
