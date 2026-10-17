package com.paytech.payment.channel.domainservice.repository;

import java.util.List;

import com.paytech.payment.channel.core.dal.dataobject.ChannelMid;

/** ChannelMid 单表只读仓储。 */
public interface ChannelMidRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<ChannelMid> findByAccountIds(List<Long> ids);

    /** 按内部主键读取，未找到返回空。 */
    ChannelMid findById(long id);
}
