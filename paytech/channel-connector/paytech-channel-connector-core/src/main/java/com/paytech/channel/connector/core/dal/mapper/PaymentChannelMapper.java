package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;

/** PaymentChannel SQL 参数绑定，SQL 仅位于 XML。 */
public interface PaymentChannelMapper {
    /** 按归属读取记录。 */
    List<PaymentChannel> findByIds(@Param("ids") List<Long> ids);

    /** 按主键读取记录。 */
    PaymentChannel findById(@Param("id") long id);
}
