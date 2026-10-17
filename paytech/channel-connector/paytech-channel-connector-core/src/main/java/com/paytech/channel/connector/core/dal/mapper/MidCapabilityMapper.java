package com.paytech.payment.channel.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.payment.channel.core.dal.dataobject.MidCapability;

/** MidCapability SQL 参数绑定，SQL 仅位于 XML。 */
public interface MidCapabilityMapper {
    /** 按归属读取记录。 */
    List<MidCapability> findByMidIds(@Param("ids") List<Long> ids);

}
