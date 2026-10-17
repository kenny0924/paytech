package com.paytech.platform.support.delivery.ext.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.delivery.core.constant.NotificationProviderTypes;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventConfiguration;
import com.paytech.platform.support.delivery.service.NotificationEventService;
import com.paytech.platform.support.delivery.service.processor.PollNotificationEventsProcessor;
import com.paytech.platform.support.delivery.service.processor.request.PollNotificationEventsRequest;

import lombok.RequiredArgsConstructor;

/**
 * 定时拉取 SES 投递事件的调度入口
 * <li>每个 Platform 实例独立运行；SQS 可见性超时保证同一消息同时只被一个实例处理，重复到达由条件更新兜底</li>
 * <li>逐个账号委托 PollNotificationEventsProcessor；一个账号失败不影响其他账号与下一轮</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(SesEventConfiguration.ENABLED)
public class NotificationEventPollJob {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventPollJob.class);

    private final NotificationEventService events;
    private final PollNotificationEventsProcessor processor;

    /** 拉取全部已配置事件队列的 SES 账号；间隔取 notification.ses.events.poll-interval。 */
    @Scheduled(initialDelayString = "${notification.ses.events.poll-interval:PT60S}",
        fixedDelayString = "${notification.ses.events.poll-interval:PT60S}")
    public void poll() {
        for (NotificationProvider account : events.eventSources(NotificationProviderTypes.AMAZON_SES)) {
            pollAccount(account);
        }
    }

    /** 单个账号的一轮拉取；未预期的本地错误只记录账号编码与异常类型，下一轮重试。 */
    void pollAccount(NotificationProvider account) {
        try {
            processor.process(new PollNotificationEventsRequest(account.getProviderId()));
        } catch (RuntimeException failure) {
            // 调度线程不能因单个账号中断；异常可能携带 SQL 或消息片段，只记录类型。
            LOG.warn("通知事件拉取失败: provider={}, error={}", account.getProviderCode(),
                failure.getClass().getSimpleName());
        }
    }
}
