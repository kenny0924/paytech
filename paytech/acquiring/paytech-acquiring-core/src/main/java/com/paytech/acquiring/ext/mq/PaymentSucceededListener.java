package com.paytech.commerce.ext.mq;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.paytech.commerce.service.processor.order.ApplyPaymentSucceededProcessor;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.rabbitmq.client.Channel;

import lombok.AllArgsConstructor;

/**
 * 消费 Payment 支付成功事件（队列 cg_commerce_payment_result），本地事务提交后才确认
 * <li>信封不合法或业务明确拒绝：拒绝且不重新入队，进入死信队列 cg_commerce_payment_result_dlq。</li>
 * <li>并发冲突及其他失败（数据库暂不可用等）：重新入队，由 Quorum Queue 投递次数上限兜底转入死信队列。</li>
 */
@Component
@AllArgsConstructor
public class PaymentSucceededListener {
    /** 消费队列。 */
    public static final String QUEUE = "cg_commerce_payment_result";

    private static final Logger LOG = LoggerFactory.getLogger(PaymentSucceededListener.class);

    private final ApplyPaymentSucceededProcessor processor;

    /** 手动确认；日志只记录消息 ID，不记录正文。 */
    @RabbitListener(queues = QUEUE, containerFactory = "paymentResultListenerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        try {
            var result = processor.process(PaymentSucceededMessageConverter.convert(message.getBody()));
            LOG.info("Payment succeeded event {} handled: {}", messageId, result);
            channel.basicAck(tag, false);
        } catch (ConcurrentRequestException conflict) {
            LOG.warn("Payment succeeded event {} conflicted and requeued", messageId);
            channel.basicNack(tag, false, true);
        } catch (IllegalArgumentException | FailException rejected) {
            LOG.warn("Payment succeeded event {} rejected to dead letter", messageId);
            channel.basicReject(tag, false);
        } catch (RuntimeException retryable) {
            LOG.warn("Payment succeeded event {} requeued", messageId, retryable);
            channel.basicNack(tag, false, true);
        }
    }
}
