package com.paytech.channel.connector.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.dal.mapper.ChannelCallbackItemMapper;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackItemRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 渠道回调项持久化。 */
@Repository
@AllArgsConstructor
public class ChannelCallbackItemRepositoryImpl implements ChannelCallbackItemRepository {
    /** 本表 SQL 映射。 */
    private final ChannelCallbackItemMapper mapper;

    /** 写入回调项；主键由调用方分配，行数不为 1 属于系统错误。 */
    @Override
    public void insert(ChannelCallbackItem item) {
        if (mapper.insert(item) != 1) {
            throw new ErrorException("渠道回调项写入失败: " + item.getChannelCallbackItemId());
        }
    }

    /** 按信封读取回调项。 */
    @Override
    public List<ChannelCallbackItem> findByCallback(long callbackId) {
        return mapper.findByCallback(callbackId);
    }
}
