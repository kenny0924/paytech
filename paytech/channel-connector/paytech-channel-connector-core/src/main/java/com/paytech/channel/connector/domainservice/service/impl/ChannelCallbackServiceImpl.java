package com.paytech.channel.connector.domainservice.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCallback;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCallbackItem;
import com.paytech.channel.connector.core.enums.CallbackStatus;
import com.paytech.channel.connector.domain.ChannelCallbackContext;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackItemRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCallbackRepository;
import com.paytech.channel.connector.domainservice.service.ChannelCallbackService;

import lombok.AllArgsConstructor;

/** 渠道回调的本地保存：信封与回调项一起写入，发布状态按版本条件推进。 */
@Service
@AllArgsConstructor
public class ChannelCallbackServiceImpl implements ChannelCallbackService {
    private final TransactionTemplate transactionTemplate;
    private final ChannelCallbackRepository callbacks;
    private final ChannelCallbackItemRepository items;

    /** 参与调用方事务写入信封与回调项。 */
    @Override
    public void saveCallback(ChannelCallbackContext context) {
        callbacks.insert(context.getCallback());
        if (context.getItem() != null) {
            items.insert(context.getItem());
        }
    }

    /** 读取回调信封。 */
    @Override
    public ChannelCallback findCallback(long callbackId) {
        return callbacks.findById(callbackId);
    }

    /** 读取回调项。 */
    @Override
    public List<ChannelCallbackItem> findItems(long callbackId) {
        return items.findByCallback(callbackId);
    }

    /** 短事务把已接收的信封置为已处理。 */
    @Override
    public void markPublished(ChannelCallback callback, Instant now) {
        transactionTemplate.executeWithoutResult(status -> callbacks.updateStatus(callback, CallbackStatus.PROCESSED, now));
    }
}
