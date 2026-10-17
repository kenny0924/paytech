package com.paytech.platform.support.delivery.service;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.core.enums.NotificationEventResult;
import com.paytech.platform.support.delivery.domain.NotificationProviderEvent;

/** 供应商投递事件的本地回写；只读写本库，不访问供应商、队列或 Vault。 */
public interface NotificationEventService {

    /** 指定服务商类型下需要拉取事件的账号：已配置事件队列且已录入密钥，不论启停。 */
    List<NotificationProvider> eventSources(String providerType);

    /**
     * 在一个短事务内把事件回写到对应尝试与投递
     * <li>结果优先级只升不降，重复与乱序事件结果确定；尝试仍在发送中返回 DEFERRED</li>
     * <li>本地并发冲突抛出 ConcurrentRequestException，调用方保留消息稍后重试</li>
     *
     * @param event 已解析的投递或投诉事件，不含不处理的类型
     * @param now 本次处理时间
     * @return APPLIED、DUPLICATE、UNMATCHED 或 DEFERRED
     */
    NotificationEventResult apply(NotificationProviderEvent event, Instant now);

    /** 记录一轮拉取的运行状态：error 为空表示成功；不推进账号版本。 */
    void recordPoll(long providerId, String error, boolean received, Instant now);
}
