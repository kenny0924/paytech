package com.paytech.channel.connector.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;

/** t_channel_callback_item 的 SQL 映射。 */
public interface ChannelCallbackItemMapper {
    /** 插入一条回调项。 */
    int insert(ChannelCallbackItem item);

    /** 按回调信封读取全部回调项，按序号。 */
    List<ChannelCallbackItem> findByCallback(@Param("callbackId") long callbackId);
}
