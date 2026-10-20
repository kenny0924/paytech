package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** 渠道配置与成本版本变更记录的只读查询；不建事务，不调用外部依赖。 */
public interface ChannelConfigChangeQueryService {

    /**
     * 分页查询一个对象的变更记录
     *
     * @param targetType 被变更对象类型
     * @param targetId 被变更对象在本库的主键
     * @param paging 已校验的分页参数
     * @return 按变更记录主键降序的分页结果；没有记录时为空页
     */
    PagingResponseBody<ChannelConfigChange> listChanges(ChangeTargetType targetType, long targetId,
        PagingRequestBody paging);
}
