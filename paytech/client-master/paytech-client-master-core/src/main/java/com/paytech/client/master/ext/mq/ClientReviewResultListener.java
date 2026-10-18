package com.paytech.client.master.ext.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.client.master.service.processor.ApplyClientReviewResultProcessor;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

/**
 * 消费 compliance ClientReviewDecided 新信封（compliance.events → client-master.compliance.hash → client-master.compliance.p0..p3，
 * design/08 §3）
 * <li>paytech-messaging 先按 eventId 跳过重放、按 aggregateVersion 跳过旧版本，再调用本 Handler；本 Handler 调用必须在事务外执行的
 * Processor，故声明 {@link ConsumeTransaction#HANDLER_MANAGED}，业务幂等（提交编号与主任务 ID）保持现状。</li>
 * <li>信封内容不合法或业务明确拒绝：抛出 {@link DomainEventRejectedException}，直接进入死信队列 client-master.compliance.dlq。</li>
 * <li>并发冲突及其他失败（数据库或依赖暂不可用）：原样抛出，由 paytech-messaging 退避后重新投递，投递上限后进入死信队列。</li>
 */
@DomainEventHandler(eventType = ClientReviewResultMessageConverter.EVENT_TYPE, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public class ClientReviewResultListener implements DomainEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ClientReviewResultListener.class);

    private final ApplyClientReviewResultProcessor processor;

    /** 只依赖结果落地用例。 */
    public ClientReviewResultListener(ApplyClientReviewResultProcessor processor) {
        this.processor = processor;
    }

    /** 日志只记录事件 ID，不记录正文。 */
    @Override
    public void consume(DomainEvent event) {
        try {
            var result = processor.process(ClientReviewResultMessageConverter.convert(event));
            LOG.info("Client review result {} handled: {}", event.eventId(), result);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Client review result {} conflicted and will be redelivered", event.eventId());
            throw conflict;
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Client review result {} rejected to dead letter", event.eventId());
            throw new DomainEventRejectedException("审核结果事件被拒绝: " + event.eventId(), rejected);
        }
    }
}
