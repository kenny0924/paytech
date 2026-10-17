package com.paytech.payment.channel.domainservice.repository;

import java.util.List;

import com.paytech.payment.channel.core.dal.dataobject.ChannelAccount;

/** ChannelAccount 单表只读仓储。 */
public interface ChannelAccountRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<ChannelAccount> findByEntity(String entityNo);

    /** 按内部主键读取，未找到返回空。 */
    ChannelAccount findById(long id);
}
