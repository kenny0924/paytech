package com.paytech.channel.connector.domainservice.repository;

import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;

/** 渠道回调项数据访问；回调项写入后不可变。 */
public interface ChannelCallbackItemRepository {
    /** 写入一条回调项。 */
    void insert(ChannelCallbackItem item);

    /** 读取信封的全部回调项，按序号；没有时为空列表。 */
    List<ChannelCallbackItem> findByCallback(long callbackId);
}
