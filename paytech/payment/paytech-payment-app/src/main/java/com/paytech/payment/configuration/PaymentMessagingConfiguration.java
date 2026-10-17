package com.paytech.payment.configuration;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.payment.integration.mq.PaymentResultRabbitPublisher;

/** Payment 发布的支付结果 Exchange；消费队列与绑定由消费方声明。 */
@Configuration(proxyBeanMethods = false)
public class PaymentMessagingConfiguration {

    /** Durable Topic Exchange topic_payment_result。 */
    @Bean
    TopicExchange paymentResultExchange() {
        return new TopicExchange(PaymentResultRabbitPublisher.EXCHANGE, true, false);
    }
}
