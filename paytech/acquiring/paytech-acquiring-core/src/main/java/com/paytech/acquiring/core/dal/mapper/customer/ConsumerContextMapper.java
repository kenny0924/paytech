package com.paytech.acquiring.core.dal.mapper.customer;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Address;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;

/** 消费者资料单表查询。 */
public interface ConsumerContextMapper {
    /** 查询支付资格需要的资料存在性。 */
    boolean existsByOrderId(@Param("orderId") long orderId);

    /** 只读本订单的保护引用和版本。 */
    ConsumerContext findByOrderId(@Param("orderId") long orderId);

    /** 插入首次保存的保护引用，原始姓名和完整地址不落本库。 */
    int insert(@Param("consumer") ConsumerContext consumer, @Param("values") ProtectedConsumer values,
        @Param("emailDomain") String emailDomain, @Param("address") Address address, @Param("now") Instant now);

    /** 按原身份和版本完整替换可编辑字段。 */
    int update(@Param("consumer") ConsumerContext consumer, @Param("values") ProtectedConsumer values,
        @Param("emailDomain") String emailDomain, @Param("address") Address address, @Param("now") Instant now);
}
