package com.paytech.channel.connector.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.domain.ChannelCallbackContext;

/** 渠道回调信封与回调项的本地保存与发布状态。 */
public interface ChannelCallbackService {
    /**
     * 保存回调信封与回调项
     * <li>参与调用方事务；同一账号同一 Body 指纹由唯一索引拒绝（DuplicateKeyException 向上传播）。</li>
     *
     * @param context 已验签、已映射的上下文
     */
    void saveCallback(ChannelCallbackContext context);

    /** 读取回调信封；不存在为 null。 */
    ChannelCallback findCallback(long callbackId);

    /** 读取回调项，按序号；没有时为空列表。 */
    List<ChannelCallbackItem> findItems(long callbackId);

    /**
     * 通知已发布并收到 Broker 确认：RECEIVED → PROCESSED（自有短事务，版本条件更新）
     *
     * @param callback 原回调信封，成功后同步状态与版本
     * @param now 写入时间
     */
    void markPublished(ChannelCallback callback, Instant now);
}
