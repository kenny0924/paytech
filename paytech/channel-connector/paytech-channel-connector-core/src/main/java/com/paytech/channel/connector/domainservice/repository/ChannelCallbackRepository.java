package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.enums.CallbackStatus;

/** 渠道回调信封数据访问。 */
public interface ChannelCallbackRepository {
    /** 写入新信封；同一账号同一 Body 指纹由唯一索引拒绝（DuplicateKeyException 向上传播）。 */
    void insert(ChannelCallback callback);

    /** 按主键读取；不存在返回 null。 */
    ChannelCallback findById(long callbackId);

    /** 按原状态与版本改为目标状态，成功后同步原对象；行数不为 1 时并发冲突。 */
    void updateStatus(ChannelCallback callback, CallbackStatus toStatus, Instant now);
}
