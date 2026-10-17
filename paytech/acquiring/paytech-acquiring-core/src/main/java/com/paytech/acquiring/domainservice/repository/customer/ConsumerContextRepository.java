package com.paytech.commerce.domainservice.repository.customer;

import java.time.Instant;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ProtectedConsumer;

/** 消费者表的按需查询边界。 */
public interface ConsumerContextRepository {
    /** 支付资格仅检查已保存资料，不加载受保护引用。 */
    boolean existsByOrderId(long orderId);

    /** 查询已授权订单的资料引用，不执行 Reveal。 */
    ConsumerContext findByOrderId(long orderId);

    /** 保存保护引用及允许保留的维度；成功后同步原对象，保留原主键和创建时间。 */
    void saveConsumer(ConsumerContext consumer, ConsumerDetails details, ProtectedConsumer values, Instant now);
}
