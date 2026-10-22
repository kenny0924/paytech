package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.mapper.ChannelCallbackMapper;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 渠道回调信封持久化。 */
@Repository
@AllArgsConstructor
public class ChannelCallbackRepositoryImpl implements ChannelCallbackRepository {
    /** 本表 SQL 映射。 */
    private final ChannelCallbackMapper mapper;

    /** 写入新信封；主键由调用方分配，行数不为 1 属于系统错误。 */
    @Override
    public void insert(ChannelCallback callback) {
        if (mapper.insert(callback) != 1) {
            throw new ErrorException("渠道回调写入失败: " + callback.getChannelCallbackId());
        }
    }

    /** 读取指定信封。 */
    @Override
    public ChannelCallback findById(long callbackId) {
        return mapper.findById(callbackId);
    }

    /** 状态与版本条件更新，成功后同步原对象。 */
    @Override
    public void updateStatus(ChannelCallback callback, CallbackStatus toStatus, Instant now) {
        if (mapper.updateStatus(callback.getChannelCallbackId(), callback.getVersion(), callback.getCallbackStatus(),
            toStatus, now) != 1) {
            throw new ConcurrentRequestException("渠道回调状态已变化: " + callback.getChannelCallbackId());
        }
        callback.setCallbackStatus(toStatus);
        callback.setVersion(callback.getVersion() + 1);
        callback.setUpdateTime(now);
    }
}
