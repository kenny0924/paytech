package com.paytech.payment.channel.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.payment.channel.core.dal.dataobject.ChannelMid;

/** ChannelMid SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelMidMapper {
    /** 按归属读取记录。 */
    List<ChannelMid> findByAccountIds(@Param("ids") List<Long> ids);

    /** 按主键读取记录。 */
    ChannelMid findById(@Param("id") long id);
}
