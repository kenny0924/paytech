package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.MidCapabilityDetail;

/** MidCapabilityDetail SQL 参数绑定，SQL 仅位于 XML。 */
public interface MidCapabilityDetailMapper {
    /** 按归属读取记录。 */
    List<MidCapabilityDetail> findByCapabilityIds(@Param("ids") List<Long> ids);

}
