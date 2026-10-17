package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;

/** ChannelAccount SQL 参数绑定，SQL 仅位于 XML。 */
public interface ChannelAccountMapper {
    /** 按归属读取记录。 */
    List<ChannelAccount> findByEntity(@Param("entityNo") String entityNo);

    /** 按主键读取记录。 */
    ChannelAccount findById(@Param("id") long id);
}
