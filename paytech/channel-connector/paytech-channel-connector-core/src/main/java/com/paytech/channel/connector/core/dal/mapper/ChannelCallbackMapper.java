package com.paytech.channel.connector.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.enums.CallbackStatus;

/** t_channel_callback 的 SQL 映射。 */
public interface ChannelCallbackMapper {
    /** 插入一条回调信封；同一账号同一指纹由唯一索引拒绝。 */
    int insert(ChannelCallback callback);

    /** 按主键读取。 */
    ChannelCallback findById(@Param("id") long id);

    /** 按原状态与版本更新处理状态。 */
    int updateStatus(@Param("id") long id, @Param("version") long version, @Param("fromStatus") CallbackStatus fromStatus,
        @Param("toStatus") CallbackStatus toStatus, @Param("now") Instant now);
}
