package com.paytech.client.master.ext.mq;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import com.paytech.client.master.service.processor.ApplyClientReviewResultProcessor;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.rabbitmq.client.Channel;

/**
 * 消费 Risk 审核结果（队列 cg_cct_client_review），本地事务提交后才确认
 * <li>信封不合法或业务明确拒绝：拒绝且不重新入队，进入死信队列 cg_cct_client_review_dlq。</li>
 * <li>并发冲突及其他失败（数据库或依赖暂不可用）：重新入队，由 Quorum Queue 投递次数上限兜底转入死信队列。</li>
 */
public class ClientReviewResultListener {
    /** 消费队列。 */
    public static final String QUEUE = "cg_cct_client_review";

    private static final Logger LOG = LoggerFactory.getLogger(ClientReviewResultListener.class);

    private final ApplyClientReviewResultProcessor processor;

    /** 只依赖结果落地用例。 */
    public ClientReviewResultListener(ApplyClientReviewResultProcessor processor) {
        this.processor = processor;
    }

    /** 手动确认；日志只记录消息 ID，不记录正文。 */
    @RabbitListener(queues = QUEUE, containerFactory = "clientReviewListenerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        try {
            var result = processor.process(ClientReviewResultMessageConverter.convert(message.getBody()));
            LOG.info("Client review result {} handled: {}", messageId, result);
            channel.basicAck(tag, false);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Client review result {} conflicted and requeued", messageId);
            channel.basicNack(tag, false, true);
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Client review result {} rejected to dead letter", messageId);
            channel.basicReject(tag, false);
        } catch (RuntimeException retryable) {
            LOG.warn("Client review result {} requeued", messageId, retryable);
            channel.basicNack(tag, false, true);
        }
    }
}
