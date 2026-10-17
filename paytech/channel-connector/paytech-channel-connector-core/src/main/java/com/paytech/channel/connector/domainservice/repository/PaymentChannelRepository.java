package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;

/** PaymentChannel 单表只读仓储。 */
public interface PaymentChannelRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<PaymentChannel> findByIds(List<Long> ids);

    /** 按内部主键读取，未找到返回空。 */
    PaymentChannel findById(long id);
}
